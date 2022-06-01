package com.wiligsi.plump.server;


import static com.wiligsi.plump.common.PlumpOuterClass.*;
import static com.wiligsi.plump.server.assertion.PlumpAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.common.PlumpGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ServerTests {
  private static final String TEST_LOCK_NAME = "testLock";

  private Server plumpServer;
  private ManagedChannel plumpChannel;
  private PlumpGrpc.PlumpBlockingStub plumpBlockingStub;

  @BeforeEach
  public void createInProcessServerClient() throws IOException, NoSuchAlgorithmException {
    String serverName = InProcessServerBuilder.generateName();
    plumpServer = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(new PlumpImpl())
        .build();
    plumpServer.start();

    plumpChannel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build();

    plumpBlockingStub = PlumpGrpc.newBlockingStub(
        plumpChannel
    );

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

  @Nested
  public class CreateLockTests {
    @Test
    public void itShouldReturnACodeOnLockCreation() {
      final CreateLockResponse response = createTestLock();

      assertThat(response).hasFieldOrProperty("destroyKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DamnLongLockName23232323232323", "sho", "$ymb)l*"})
    public void itShouldRejectMalformedLockNames(String lockName) {
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> createLock(lockName),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidLockNameException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"testLock", "TeSTLOck", "testlock"})
    public void itShouldRejectDuplicateLockNames(String duplicateName) {
      createTestLock();

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> createLock(duplicateName),
          StatusRuntimeException.class
      );

      assertThat(throwable).isLockNameAlreadyExistsExceptionFor(duplicateName);
    }
  }

  @Nested
  public class DestroyLockTests {
    @Test
    public void itShouldNotDestroyANonExistentLock() {
      final String nonExistentName = "fakeLock";

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> destroyLock(nonExistentName, "fakekey"),
          StatusRuntimeException.class
      );

      assertThat(throwable).isLockNameNotFoundExceptionFor(nonExistentName);
    }

    @Test
    public void itShouldBeAbleToDestroyALock() {
      final String destroyKey = createTestLock().getDestroyKey();

      assertThatCode(
          () -> destroyTestLock(destroyKey)
      ).doesNotThrowAnyException();
    }

    @Test
    public void itShouldOnlyDestroyALockWithTheRightCode() {
      createTestLock();

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> destroyTestLock("fakeKey"),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidDestroyKeyExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldOnlyDestroyOnce() {
      final String destroyKey = createTestLock().getDestroyKey();
      destroyTestLock(destroyKey);

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> destroyTestLock(destroyKey),
          StatusRuntimeException.class
      );

      assertThat(throwable).isLockNameNotFoundExceptionFor(TEST_LOCK_NAME);
    }
  }

  @Nested
  public class AcquireSequencerTests {
    @Test
    public void itShouldNotBeAbleToGetSequencerFromNonExistentLock() {
      final String fakeLockName = "fakeLock";
      // TODO: Can you make malformed requests?
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> {
            Sequencer fakeSequencer = acquireSequencer(fakeLockName);
            assertThat(fakeSequencer)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("lockName", fakeLockName);
          },
          StatusRuntimeException.class
      );

      assertThat(throwable).isLockNameNotFoundExceptionFor(fakeLockName);
    }

    @Test
    public void itShouldBeAbleToGetSequencerFromLock() {
      final Instant effectiveTime = Instant.now();
      createTestLock();

      final Sequencer testSequencer = acquireTestLockSequencer();

      assertThat(testSequencer)
          .hasNoNullFieldsOrProperties()
          .hasFieldOrPropertyWithValue("lockName", TEST_LOCK_NAME)
          .hasFieldOrPropertyWithValue("sequenceNumber", 0);

      assertThat(testSequencer.getExpiration()).isGreaterThanOrEqualTo(effectiveTime.toEpochMilli());
    }

    @Test
    public void itShouldNotLockWhenLockDoesNotExist() {
      final String fakeLockName = "fakeLock";

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> acquireLock(
              Sequencer.newBuilder()
                  .setLockName("fakeLock")
                  .build()
          ),
          StatusRuntimeException.class
      );
      assertThat(throwable).isLockNameNotFoundExceptionFor(fakeLockName);
      // TODO: provide protection against partial sequencers
    }
  }

  @Nested
  public class AcquireLockTests {
    @Test
    public void itShouldLockWhenSequencerIsHead() {
      createTestLock();
      Sequencer sequencer = acquireTestLockSequencer();
      LockResponse lockResponse = acquireLock(sequencer);

      assertThat(lockResponse).hasNoNullFieldsOrProperties()
          .hasFieldOrPropertyWithValue("success", true);
      assertThat(lockResponse.getUpdatedSequencer())
          .hasFieldOrPropertyWithValue("lockName", sequencer.getLockName())
          .hasFieldOrPropertyWithValue("sequenceNumber", sequencer.getSequenceNumber());
      assertThat(lockResponse.getUpdatedSequencer().getKey()).isNotEqualTo(sequencer.getKey());
      assertThat(lockResponse.getUpdatedSequencer().getExpiration()).isGreaterThanOrEqualTo(
          sequencer.getExpiration());
    }

    @Test
    public void itShouldNotLockWhenSequencerIsNotHead() {
      createTestLock();

      acquireTestLockSequencer();
      Sequencer secondSequencer = acquireTestLockSequencer();

      LockResponse failureResponse = acquireLock(secondSequencer);

      assertThat(failureResponse).hasNoNullFieldsOrProperties()
          .hasFieldOrPropertyWithValue("success", false);
      assertThat(failureResponse.getUpdatedSequencer()).isUpdatedFrom(secondSequencer);
    }

    @Test
    public void itShouldNotLockWithOldSequencerWhenUpdated() {
      createTestLock();
      Sequencer sequencer = acquireTestLockSequencer();
      acquireLock(sequencer);

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> acquireLock(sequencer),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldErrorOnNullSequencer() {
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> plumpBlockingStub.acquireLock(LockRequest.newBuilder().build()),
          StatusRuntimeException.class
      );

      assertThat(throwable).isNullSequencerException();
    }
  }

  @Nested
  public class KeepAliveTests {
    @Test
    public void itShouldKeepAliveSequencer() {
      createTestLock();
      Sequencer testLockSequencer = acquireTestLockSequencer();
      Sequencer keepAliveSequencer = keepAliveSequencer(testLockSequencer);

      assertThat(keepAliveSequencer).isUpdatedFrom(testLockSequencer);
    }

    @Test
    public void itShouldNotKeepAliveInvalidSequencer() {
      createTestLock();
      Sequencer testLockSequencer = acquireTestLockSequencer();
      Sequencer badSequencer = Sequencer.newBuilder(testLockSequencer)
          .setKey("badKey")
          .build();

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> keepAliveSequencer(badSequencer),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldErrorOnNullSequencer() {
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> plumpBlockingStub.keepAlive(KeepAliveRequest.newBuilder().build()),
          StatusRuntimeException.class
      );

      assertThat(throwable).isNullSequencerException();
    }
  }

  @Nested
  public class ReleaseLockTests {
    @Test
    public void itShouldBeAbleToReleaseALock() {
      createTestLock();
      Sequencer sequencer = acquireTestLockSequencer();
      LockResponse Response = acquireLock(sequencer);
      Sequencer lockSequencer = Response.getUpdatedSequencer();
      ReleaseResponse releaseResponse = releaseLock(lockSequencer);

      assertThat(lockSequencer).isUpdatedFrom(sequencer);
      assertThat(releaseResponse)
          .hasFieldOrPropertyWithValue("success", true);
    }

    @Test
    public void itShouldNotBeAbleToReleaseLockWithOtherSequencer() {
      createTestLock();
      Sequencer lockSequencer = acquireTestLockSequencer();
      Sequencer badUnlockSequencer = acquireTestLockSequencer();

      acquireLock(lockSequencer);
      ReleaseResponse releaseResponse = releaseLock(badUnlockSequencer);

      assertThat(releaseResponse).hasNoNullFieldsOrProperties()
          .hasFieldOrPropertyWithValue("success", false);
      assertThat(releaseResponse.getUpdatedSequencer()).isUpdatedFrom(badUnlockSequencer);
    }

    @Test
    public void itShouldNotBeReleasedByAnInvalidSequencer() {
      createTestLock();
      Sequencer sequencer = acquireTestLockSequencer();
      acquireLock(sequencer);

      StatusRuntimeException throwable = catchThrowableOfType(
          () -> releaseLock(sequencer),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldErrorOnNullSequencer() {
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> plumpBlockingStub.releaseLock(ReleaseRequest.newBuilder().build()),
          StatusRuntimeException.class
      );

      assertThat(throwable).isNullSequencerException();
    }
  }

  @Nested
  public class WhoHasTests {

    @Test
    public void itShouldIndicateIfNobodyHasTheLock() {
      createTestLock();
      assertThat(whoHasTestLock())
          .hasFieldOrPropertyWithValue("locked", false)
          .hasFieldOrPropertyWithValue("sequenceNumber", 0);
    }

    @Test
    public void itShouldIndicateIfSomeoneHasTheLock() {
      createTestLock();
      final Sequencer sequencer = acquireTestLockSequencer();
      acquireLock(sequencer);
      assertThat(whoHasTestLock())
          .hasNoNullFieldsOrProperties()
          .hasFieldOrPropertyWithValue("locked", true)
          .hasFieldOrPropertyWithValue("sequenceNumber", 0);
    }

    @Test
    public void itShouldErrorOnWhoHasWithNonexistantLock() {
      final StatusRuntimeException throwable = catchThrowableOfType(
          ServerTests.this::whoHasTestLock,
          StatusRuntimeException.class
      );

      assertThat(throwable).isLockNameNotFoundExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldErrorOnWhoHasWithMalformedLockName() {
      final String tooLongName = "ThisNameisTooLongAndInvalid";
      final StatusRuntimeException throwable = catchThrowableOfType(
          () -> whoHasLock(tooLongName),
          StatusRuntimeException.class
      );

      assertThat(throwable).isInvalidLockNameException();
    }
  }

  @Nested
  public class GetNextSequenceNumberTests {
    @Test
    public void itShouldGetTheNextSequenceNumberFromValidLock() {
      createTestLock();
      acquireTestLockSequencer();
      acquireTestLockSequencer();
      assertThat(getNextTestLockSequenceNumber())
          .hasFieldOrPropertyWithValue("sequenceNumber", 2);
    }

    @Test
    public void itShouldThrowErrorOnNonExistantLock() {
      StatusRuntimeException throwable = catchThrowableOfType(
          ServerTests.this::getNextTestLockSequenceNumber,
          StatusRuntimeException.class
      );
      assertThat(throwable).isLockNameNotFoundExceptionFor(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldThrowErrorOnInvalidLockName() {
      StatusRuntimeException throwable = catchThrowableOfType(
          () -> getNextSequenceNumber("Inv#*&$)(@#*&$alid"),
          StatusRuntimeException.class
      );
      assertThat(throwable).isInvalidLockNameException();
    }
  }

  @Nested
  public class ListLocksTests {
    @Test
    public void itShouldReturnEmptyListForNoLocks() {
      assertThat(listLocks().getLockNamesCount()).isEqualTo(0);
    }

    // Should display all locks when requested
    @Test
    public void itShouldReturnListOfAllLocks() {
      final String otherLockName = "otherLock";
      createTestLock();
      createLock(otherLockName);
      final ListResponse list = listLocks();
      assertThat(list.getLockNamesCount()).isEqualTo(2);
      assertThat(list.getLockNamesList()).contains(TEST_LOCK_NAME, otherLockName);
    }
  }

  // Helper Methods

  private CreateLockResponse createLock(String lockName) {
    return plumpBlockingStub.createLock(
        CreateLockRequest
            .newBuilder()
            .setLockName(lockName)
            .build()
    );
  }

  private CreateLockResponse createTestLock() {
    return createLock(TEST_LOCK_NAME);
  }

  private void destroyLock(String lockName, String destroyKey) {
    DestroyLockResponse destroyLockResponse = plumpBlockingStub.destroyLock(
        DestroyLockRequest.newBuilder()
            .setLockName(lockName)
            .setDestroyKey(destroyKey)
            .build()
    );
    assertThat(destroyLockResponse.isInitialized()).isTrue();
  }

  private void destroyTestLock(String destroyKey) {
    destroyLock(TEST_LOCK_NAME, destroyKey);
  }

  private Sequencer acquireSequencer(String lockName) {
    SequencerResponse sequencerResponse = plumpBlockingStub.acquireSequencer(
        SequencerRequest.newBuilder()
            .setLockName(lockName)
            .build()
    );
    return sequencerResponse.getSequencer();
  }

  private Sequencer acquireTestLockSequencer() {
    return acquireSequencer(TEST_LOCK_NAME);
  }

  private LockResponse acquireLock(Sequencer sequencer) {
    return plumpBlockingStub.acquireLock(
        LockRequest.newBuilder()
            .setSequencer(sequencer)
            .build()
    );
  }

  private Sequencer keepAliveSequencer(Sequencer sequencer) {
    return plumpBlockingStub.keepAlive(
        KeepAliveRequest.newBuilder()
            .setSequencer(sequencer)
            .build()
    ).getUpdatedSequencer();
  }

  private ReleaseResponse releaseLock(Sequencer sequencer) {
    return plumpBlockingStub.releaseLock(
        ReleaseRequest.newBuilder()
            .setSequencer(sequencer)
            .build()
    );
  }

  private WhoHasResponse whoHasLock(String lockName) {
    return plumpBlockingStub.whoHasLock(
        WhoHasRequest.newBuilder()
            .setLockName(lockName)
            .build()
    );
  }

  private WhoHasResponse whoHasTestLock() {
    return whoHasLock(TEST_LOCK_NAME);
  }

  private NextSequencerResponse getNextSequenceNumber(String lockName) {
    return plumpBlockingStub.nextSequencer(
        NextSequencerRequest.newBuilder()
            .setLockName(lockName)
            .build()
    );
  }

  private NextSequencerResponse getNextTestLockSequenceNumber() {
    return getNextSequenceNumber(TEST_LOCK_NAME);
  }

  private ListResponse listLocks() {
    return plumpBlockingStub.listLocks(
        ListRequest.newBuilder().build()
    );
  }
}