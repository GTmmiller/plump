package com.wiligsi.plump.server.lock;

import static com.wiligsi.plump.common.PlumpOuterClass.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.wiligsi.plump.server.assertion.PlumpAssertions.assertThat;

import com.wiligsi.plump.server.InvalidSequencerException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
