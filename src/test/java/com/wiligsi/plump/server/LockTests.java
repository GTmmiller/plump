package com.wiligsi.plump.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static com.wiligsi.plump.PlumpOuterClass.*;

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
    public void beforeEach() {
        try {
            testLock = new Lock(TEST_LOCK_NAME);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new RuntimeException(noSuchAlgorithmException);
        }
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
    public void itShouldNotLockWhenNew() throws NoSuchAlgorithmException {
        final Sequencer dud = dudSequencerSupplier.get();

        assertThat(testLock.lock(dud)).isFalse();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).isEmpty();
    }

    @Test
    public void itShouldNotUnlockWhenNew() throws NoSuchAlgorithmException {
        final Sequencer dud = dudSequencerSupplier.get();

        assertThat(testLock.unlock(dud)).isFalse();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).isEmpty();
    }

    @Test
    public void itShouldLockWhenReady() throws NoSuchAlgorithmException {
        final Sequencer sequencer = testLock.createSequencer();

        assertThat(testLock.lock(sequencer)).isTrue();
        assertLocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).isPresent().contains(sequencer.getSequenceNumber());
    }

    @Test
    public void itShouldUnlockWhenReady() throws NoSuchAlgorithmException {
        final Sequencer sequencer = testLock.createSequencer();

        testLock.lock(sequencer);
        assertThat(testLock.unlock(sequencer)).isTrue();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).isEmpty();
    }

    @Test
    public void itShouldNotLetASequencerBeUsedTwice() throws NoSuchAlgorithmException {
        final Sequencer sequencer = testLock.createSequencer();

        testLock.lock(sequencer);
        testLock.unlock(sequencer);
        assertThat(testLock.lock(sequencer)).isFalse();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).isEmpty();
    }

    @Test
    public void itShouldOnlyLockWithHeadSequencer() throws NoSuchAlgorithmException {
        final Sequencer sequencer =  testLock.createSequencer();
        final Sequencer secondarySequencer = testLock.createSequencer();

        assertThat(testLock.lock(secondarySequencer)).isFalse();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
    }

    @Test
    public void itShouldOnlyUnlockWithHeadSequencer() throws NoSuchAlgorithmException {
        final Sequencer sequencer = testLock.createSequencer();
        final Sequencer secondarySequencer = testLock.createSequencer();

        testLock.lock(sequencer);
        assertThat(testLock.unlock(secondarySequencer)).isFalse();
        assertLocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
    }

    @Test
    public void itShouldImplicitlyRemoveOverdueSequencer() throws NoSuchAlgorithmException {
        final Sequencer overdueSequencer = testLock.createSequencer();
        final Sequencer onTimeSequencer;

        setTestClockAhead(Duration.ofMinutes(1));
        onTimeSequencer = testLock.createSequencer();
        setTestClockAhead(Duration.ofMinutes(2));

        assertThat(testLock.unlock(overdueSequencer)).isFalse();
        assertUnlocked(testLock);
        assertThat(testLock.getHeadSequencerNumber()).contains(onTimeSequencer.getSequenceNumber());
    }

    @Test
    public void itShouldUnlockWhenHeadSequencerIsOverdue() throws NoSuchAlgorithmException {
        final Sequencer overdueSequencer = testLock.createSequencer();
        final Sequencer onTimeSequencer;

        testLock.lock(overdueSequencer);
        setTestClockAhead(Duration.ofMinutes(1));
        onTimeSequencer = testLock.createSequencer();
        setTestClockAhead(Duration.ofMinutes(2));

        assertThat(testLock.getHeadSequencerNumber()).contains(onTimeSequencer.getSequenceNumber());
        assertUnlocked(testLock);
    }

    @Test
    public void itShouldKeepSequencerAlive() throws NoSuchAlgorithmException {
        final Sequencer sequencer = testLock.createSequencer();
        setTestClockAhead(Duration.ofMinutes(1));
        final Optional<Sequencer> aliveSequencer = testLock.keepAlive(sequencer);
        setTestClockAhead(Duration.ofMinutes(2));

        assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
        assertThat(aliveSequencer).isPresent();
        assertThat(sequencer.getLockName()).isEqualTo(aliveSequencer.get().getLockName());
        assertThat(sequencer.getSequenceNumber()).isEqualTo(aliveSequencer.get().getSequenceNumber());
        assertThat(sequencer.getExpiration()).isLessThan(aliveSequencer.get().getExpiration());
        assertThat(sequencer.getKey()).isNotEqualTo(aliveSequencer.get().getKey());
    }

    @Test
    public void itShouldKeepSequencerAliveInPlace() throws NoSuchAlgorithmException {
        final Sequencer headSequencer = testLock.createSequencer();
        final Sequencer keepAliveSequencer = testLock.createSequencer();
        setTestClockAhead(Duration.ofMinutes(1));
        testLock.keepAlive(keepAliveSequencer);
        assertThat(testLock.getHeadSequencerNumber()).contains(headSequencer.getSequenceNumber());
    }

    @Test
    public void itShouldReturnEmptyForDudKeepAlive() throws NoSuchAlgorithmException {
        final Sequencer dudSequencer = dudSequencerSupplier.get();
        setTestClockAhead(Duration.ofMinutes(1));
        final Optional<Sequencer> renewedDud = testLock.keepAlive(dudSequencer);

        assertThat(renewedDud).isEmpty();
    }

    // Can't lock with wrong lock Name

    // Can't lock with wrong expiration

    // Can't verify? --> move to sequencer util

    // Can't unlock with post keep alive sequencer

    /*
     * Helper Methods
     */
    private void assertUnlocked(Lock lock) {
        assertThat(lock.getState()).isEqualTo(LockState.UNLOCKED);
    }

    private void assertLocked(Lock lock) {
        assertThat(lock.getState()).isEqualTo(LockState.LOCKED);
    }

    private void setTestClockAhead(Duration duration) {
        testLock.setClock(Clock.offset(testClock, duration));
    }
}
