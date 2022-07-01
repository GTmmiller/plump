package com.wiligsi.plump.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.client.PlumpClient;
import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import com.wiligsi.plump.server.PlumpImpl;
import com.wiligsi.plump.server.lock.PlumpLock;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PhilosopherTest {

  private static final String TEST_LOCK = "testLock";

  private ManagedChannel plumpChannel;
  private Server plumpServer;
  private PlumpClient plumpClient;

  private Philosopher philosopher;

  @BeforeEach
  public void createInProcessServerClient() throws IOException, NoSuchAlgorithmException {
    String serverName = InProcessServerBuilder.generateName();
    plumpServer = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(new PlumpImpl(PlumpLock::new))
        .build();
    plumpServer.start();

    plumpChannel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build();

    plumpClient = new PlumpClient(plumpChannel);
    plumpClient.createLock(TEST_LOCK);

    philosopher = new Philosopher(plumpClient, TEST_LOCK);
  }

  @AfterEach
  public void terminateInProcessServerClient() throws InterruptedException {
    plumpChannel.shutdown();
    plumpServer.shutdown();

    try {
      assert plumpChannel.awaitTermination(5,
          TimeUnit.SECONDS) : "Channel could not be shutdown in reasonable time";
      assert plumpServer.awaitTermination(5,
          TimeUnit.SECONDS) : "Server could not be shutdown in reasonable time";
    } finally {
      plumpChannel.shutdownNow();
      plumpServer.shutdownNow();
    }
  }


  @Test
  public void itShouldBecomeHungryWhen25OrUnder() {
    plumpClient.acquireSequencer(TEST_LOCK);

    takeNTurns(75);

    assertThat(philosopher).hasFieldOrPropertyWithValue("food", 25)
        .hasFieldOrPropertyWithValue("state", PhilosopherState.HUNGRY);
  }

  @Test
  public void itShouldDieWhenFoodIsZero() {
    plumpClient.acquireSequencer(TEST_LOCK);

    takeNTurns(100);

    assertThat(philosopher).hasFieldOrPropertyWithValue("food", 0)
        .hasFieldOrPropertyWithValue("state", PhilosopherState.DEAD)
        .hasFieldOrPropertyWithValue("turnDiedOn", 100);
  }

  @Test
  public void itShouldStayDeadWhenDead() {
    Sequencer unlockSequencer = plumpClient.acquireSequencer(TEST_LOCK);

    takeNTurns(100);

    unlockSequencer = plumpClient.acquireLock(unlockSequencer).getUpdatedSequencer();
    plumpClient.releaseLock(unlockSequencer);

    takeNTurns(1);

    assertThat(philosopher).hasFieldOrPropertyWithValue("food", 0)
        .hasFieldOrPropertyWithValue("state", PhilosopherState.DEAD)
        .hasFieldOrPropertyWithValue("turnDiedOn", 100);
  }

  @Test
  public void itShouldBeAbleToFeedFromLock() {
    takeNTurns(75);

    assertThat(philosopher).hasFieldOrPropertyWithValue("food", 25)
        .hasFieldOrPropertyWithValue("state", PhilosopherState.HUNGRY);

    takeNTurns(1);

    assertThat(philosopher).hasFieldOrPropertyWithValue("food", 100)
        .hasFieldOrPropertyWithValue("state", PhilosopherState.THINKING);
  }

  // Helper Methods
  // TODO: Seriously consider mocking the client and/or making the lock a functional interface

  public void takeNTurns(int n) {
    for (int i = 0; i < n; i++) {
      philosopher.takeTurn();
    }
  }

}
