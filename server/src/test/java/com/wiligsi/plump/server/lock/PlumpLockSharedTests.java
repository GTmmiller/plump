package com.wiligsi.plump.server.lock;

import static com.wiligsi.plump.server.assertion.PlumpAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import com.wiligsi.plump.server.InvalidSequencerException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


/**
 * A Collection of tests that applies to PlumpLocks and any subclass of PlumpLock
 */
public class PlumpLockSharedTests {

  private static final String TEST_LOCK_NAME = "paramLock";

  private static final Supplier<Sequencer> dudSequencerSupplier = () -> Sequencer.newBuilder()
      .setLockName(TEST_LOCK_NAME)
      .setExpiration(Instant.now().plus(Duration.ofDays(1)).toEpochMilli())
      .setSequenceNumber(0)
      .setKey("theDud")
      .build();

  private static Clock testClock;


  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldVerifyStartingState(PlumpLock paramLock) {
    assertThat(paramLock)
        .hasFieldOrPropertyWithValue("state", LockState.UNLOCKED)
        .hasFieldOrPropertyWithValue("headSequencerNumber", Optional.empty());
    Assertions.assertThat(paramLock.getName().getDisplayName()).isEqualTo(TEST_LOCK_NAME);
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldNotLockWhenNew(PlumpLock paramLock) {
    final Sequencer dud = dudSequencerSupplier.get();

    assertThatThrownBy(
        () -> paramLock.acquire(dud)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(paramLock).isUnlocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldNotUnlockWhenNew(PlumpLock paramLock) {
    final Sequencer dud = dudSequencerSupplier.get();

    assertThatThrownBy(
        () -> paramLock.release(dud)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(paramLock).isUnlocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldLockWhenReady(PlumpLock paramLock) throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();

    Assertions.assertThat(paramLock.acquire(sequencer)).isTrue();
    assertThat(paramLock).isLocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber()).isPresent()
        .contains(sequencer.getSequenceNumber());
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldOnlyUnlockWithHeadSequencer(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();
    final Sequencer secondarySequencer = paramLock.createSequencer();

    paramLock.acquire(sequencer);
    Assertions.assertThat(paramLock.release(secondarySequencer)).isFalse();
    assertThat(paramLock).isLocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber())
        .contains(sequencer.getSequenceNumber());
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldNotLockIfAlreadyLocked(PlumpLock paramLock) throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();
    paramLock.acquire(sequencer);
    Assertions.assertThat(paramLock.acquire(sequencer)).isFalse();
    assertThat(paramLock).isLocked();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldUnlockWhenReady(PlumpLock paramLock) throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();

    paramLock.acquire(sequencer);
    Assertions.assertThat(paramLock.release(sequencer)).isTrue();
    assertThat(paramLock).isUnlocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldNotLetASequencerBeUsedTwice(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();

    paramLock.acquire(sequencer);
    paramLock.release(sequencer);
    assertThatThrownBy(
        () -> paramLock.acquire(sequencer)
    ).isInstanceOf(InvalidSequencerException.class);
    assertThat(paramLock).isUnlocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldImplicitlyRemoveOverdueSequencer(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer overdueSequencer = paramLock.createSequencer();
    final Sequencer onTimeSequencer;

    setTestClockAhead(paramLock, Duration.ofMinutes(1));
    onTimeSequencer = paramLock.createSequencer();
    setTestClockAhead(paramLock, Duration.ofMinutes(2));

    Assertions.assertThat(paramLock.release(overdueSequencer)).isFalse();
    assertThat(paramLock).isUnlocked();
    Assertions.assertThat(paramLock.getHeadSequencerNumber())
        .contains(onTimeSequencer.getSequenceNumber());
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldUnlockWhenHeadSequencerIsOverdue(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer overdueSequencer = paramLock.createSequencer();
    final Sequencer onTimeSequencer;

    paramLock.acquire(overdueSequencer);
    setTestClockAhead(paramLock, Duration.ofMinutes(1));
    onTimeSequencer = paramLock.createSequencer();
    setTestClockAhead(paramLock, Duration.ofMinutes(2));

    Assertions.assertThat(paramLock.getHeadSequencerNumber())
        .contains(onTimeSequencer.getSequenceNumber());
    assertThat(paramLock).isUnlocked();
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldKeepSequencerAlive(PlumpLock paramLock) throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();
    setTestClockAhead(paramLock, Duration.ofMinutes(1));
    final Sequencer aliveSequencer = paramLock.keepAlive(sequencer);
    setTestClockAhead(paramLock, Duration.ofMinutes(2));

    Assertions.assertThat(paramLock.getHeadSequencerNumber())
        .contains(sequencer.getSequenceNumber());
    assertThat(aliveSequencer).isUpdatedFrom(sequencer);
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldKeepSequencerAliveInPlace(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer headSequencer = paramLock.createSequencer();
    final Sequencer keepAliveSequencer = paramLock.createSequencer();
    setTestClockAhead(paramLock, Duration.ofMinutes(1));
    paramLock.keepAlive(keepAliveSequencer);
    Assertions.assertThat(paramLock.getHeadSequencerNumber())
        .contains(headSequencer.getSequenceNumber());
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldThrowExceptionForDudKeepAlive(PlumpLock paramLock) {
    final Sequencer dudSequencer = dudSequencerSupplier.get();
    setTestClockAhead(paramLock, Duration.ofMinutes(1));

    assertThatThrownBy(
        () -> paramLock.keepAlive(dudSequencer)
    ).isInstanceOf(InvalidSequencerException.class);
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldRevokeAValidSequencer(PlumpLock paramLock) throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();
    paramLock.revokeSequencer(sequencer);

    assertThatThrownBy(
        () -> paramLock.acquire(sequencer)
    ).isInstanceOf(InvalidSequencerException.class);
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldThrowExceptionForDudRevoke(PlumpLock paramLock) {
    final Sequencer dudSequencer = dudSequencerSupplier.get();

    assertThatThrownBy(
        () -> paramLock.revokeSequencer(dudSequencer)
    ).isInstanceOf(InvalidSequencerException.class);
  }

  @ParameterizedTest
  @MethodSource("provideLocks")
  public void itShouldRevokeAndUnlockWhenUsedOnLockedHead(PlumpLock paramLock)
      throws InvalidSequencerException {
    final Sequencer sequencer = paramLock.createSequencer();

    paramLock.acquire(sequencer);
    paramLock.revokeSequencer(sequencer);

    assertThat(paramLock).isUnlocked();
    assertThatThrownBy(
        () -> paramLock.release(sequencer)
    ).isInstanceOf(InvalidSequencerException.class);
  }

  /*
   * Helper Methods
   */

  private static void setTestClockAhead(PlumpLock lock, Duration duration) {
    lock.setClock(Clock.offset(testClock, duration));
  }

  private static Stream<Arguments> provideLocks() {
    final PlumpLock plumpLock = new PlumpLock(TEST_LOCK_NAME);
    testClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    plumpLock.setClock(testClock);

    final PlumpLock slimLock = new SlimLock(TEST_LOCK_NAME);
    slimLock.setClock(testClock);

    return Stream.of(
        Arguments.of(plumpLock),
        Arguments.of(slimLock)
    );
  }
}
