package com.wiligsi.plump.server.lock;

import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import static com.wiligsi.plump.server.assertion.PlumpAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.server.InvalidSequencerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Individual tests for locks with differing behaviors
 */
public class LockTests {

  private static final String TEST_LOCK_NAME = "testLock";

  private Lock testLock;


  @Nested
  public class PlumpLockTests {

    @BeforeEach
    public void beforeEach() {
      testLock = new PlumpLock(TEST_LOCK_NAME);
    }

    @Test
    public void itShouldOnlyLockWithHeadSequencer() throws InvalidSequencerException {
      final Sequencer sequencer = testLock.createSequencer();
      final Sequencer secondarySequencer = testLock.createSequencer();

      assertThat(testLock.acquire(secondarySequencer)).isFalse();
      assertThat(testLock).isUnlocked();
      assertThat(testLock.getHeadSequencerNumber()).contains(sequencer.getSequenceNumber());
    }

  }

  @Nested
  public class SlimLockTests {

    @BeforeEach
    public void beforeEach() {
      testLock = new SlimLock(TEST_LOCK_NAME);
    }

    @Test
    public void itCanLockWithAnyValidSequencer() throws InvalidSequencerException {
      testLock.createSequencer();
      final Sequencer secondarySequencer = testLock.createSequencer();

      assertThat(testLock.acquire(secondarySequencer)).isTrue();
      assertThat(testLock).isLocked();
    }
  }
}
