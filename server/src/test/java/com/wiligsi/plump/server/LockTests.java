package com.wiligsi.plump.server;

import static com.wiligsi.plump.common.PlumpOuterClass.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.wiligsi.plump.server.assertion.PlumpAssertions.assertThat;

import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LockTests {

  private static final String TEST_LOCK_NAME = "testLock";

  private static final Supplier<Sequencer> dudSequencerSupplier = () -> Sequencer.newBuilder()
      .setLockName(TEST_LOCK_NAME)
      .setExpiration(Instant.now().plus(Duration.ofDays(1)).toEpochMilli())
      .setSequenceNumber(0)
      .setKey("theDud")
      .build();

  private Lock testLock;
  private Clock testClock;

  @BeforeEach
  public void beforeEach() throws NoSuchAlgorithmException {
    testLock = new Lock(TEST_LOCK_NAME);
    testClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    testLock.setClock(testClock);
  }

  @Test
  public void itShouldVerifyStartingState() {
    assertThat(testLock)
        .hasFieldOrPropertyWithValue("state", LockState.UNLOCKED)
        .hasFieldOrPropertyWithValue("headSequencerNumber", Optional.empty());
    assertThat(testLock.getName().getDisplayName()).isEqualTo(TEST_LOCK_NAME);
  }

  @Test
  public void itShouldNotLockWhenNew() {
    final Sequencer dud = dudSequencerSupplier.get();

    assertThatThrownBy(
        () -> testLock.acquire(dud)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).isEmpty();
  }

  @Test
  public void itShouldNotUnlockWhenNew() {
    final Sequencer dud = dudSequencerSupplier.get();

    assertThatThrownBy(
        () -> testLock.release(dud)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).isEmpty();
  }

  @Test
  public void itShouldLockWhenReady() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();

    assertThat(testLock.acquire(sequencer)).isTrue();
    assertThat(testLock).isLocked();
    assertThat(testLock.getHeadSequencerNumber()).isPresent()
        .contains(sequencer.getSequenceNumber());
  }

  @Test
  public void itShouldNotLockIfAlreadyLocked() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();
    testLock.acquire(sequencer);
    assertThat(testLock.acquire(sequencer)).isFalse();
    assertThat(testLock).isLocked();
  }

  @Test
  public void itShouldUnlockWhenReady() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();

    testLock.acquire(sequencer);
    assertThat(testLock.release(sequencer)).isTrue();
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).isEmpty();
  }

  @Test
  public void itShouldNotLetASequencerBeUsedTwice() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();

    testLock.acquire(sequencer);
    testLock.release(sequencer);
    assertThatThrownBy(
        () -> testLock.acquire(sequencer)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).isEmpty();
  }

  @Test
  public void itShouldOnlyLockWithHeadSequencer() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();
    final Sequencer secondarySequencer = testLock.createSequencer();

    assertThat(testLock.acquire(secondarySequencer)).isFalse();
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
  }

  @Test
  public void itShouldOnlyUnlockWithHeadSequencer() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();
    final Sequencer secondarySequencer = testLock.createSequencer();

    testLock.acquire(sequencer);
    assertThat(testLock.release(secondarySequencer)).isFalse();
    assertThat(testLock).isLocked();
    assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
  }

  @Test
  public void itShouldImplicitlyRemoveOverdueSequencer() throws InvalidSequencerException {
    final Sequencer overdueSequencer = testLock.createSequencer();
    final Sequencer onTimeSequencer;

    setTestClockAhead(Duration.ofMinutes(1));
    onTimeSequencer = testLock.createSequencer();
    setTestClockAhead(Duration.ofMinutes(2));

    assertThat(testLock.release(overdueSequencer)).isFalse();
    assertThat(testLock).isUnlocked();
    assertThat(testLock.getHeadSequencerNumber()).contains(onTimeSequencer.getSequenceNumber());
  }

  @Test
  public void itShouldUnlockWhenHeadSequencerIsOverdue() throws InvalidSequencerException {
    final Sequencer overdueSequencer = testLock.createSequencer();
    final Sequencer onTimeSequencer;

    testLock.acquire(overdueSequencer);
    setTestClockAhead(Duration.ofMinutes(1));
    onTimeSequencer = testLock.createSequencer();
    setTestClockAhead(Duration.ofMinutes(2));

    assertThat(testLock.getHeadSequencerNumber()).contains(onTimeSequencer.getSequenceNumber());
    assertThat(testLock).isUnlocked();
  }

  @Test
  public void itShouldKeepSequencerAlive() throws InvalidSequencerException {
    final Sequencer sequencer = testLock.createSequencer();
    setTestClockAhead(Duration.ofMinutes(1));
    final Sequencer aliveSequencer = testLock.keepAlive(sequencer);
    setTestClockAhead(Duration.ofMinutes(2));

    assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
    assertThat(aliveSequencer).isUpdatedFrom(sequencer);
  }

  @Test
  public void itShouldKeepSequencerAliveInPlace() throws InvalidSequencerException {
    final Sequencer headSequencer = testLock.createSequencer();
    final Sequencer keepAliveSequencer = testLock.createSequencer();
    setTestClockAhead(Duration.ofMinutes(1));
    testLock.keepAlive(keepAliveSequencer);
    assertThat(testLock.getHeadSequencerNumber()).contains(headSequencer.getSequenceNumber());
  }

  @Test
  public void itShouldThrowExceptionForDudKeepAlive() {
    final Sequencer dudSequencer = dudSequencerSupplier.get();
    setTestClockAhead(Duration.ofMinutes(1));

    assertThatThrownBy(
        () -> testLock.keepAlive(dudSequencer)
    ).isInstanceOf(InvalidSequencerException.class);
  }

  /*
   * Helper Methods
   */

  private void setTestClockAhead(Duration duration) {
    testLock.setClock(Clock.offset(testClock, duration));
  }
}
