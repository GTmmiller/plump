package com.wiligsi.plump.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CliStateSingletonTest {
  private static final String TEST_URL = "localhost:43566";
  private static final String TEST_LOCK_NAME = "testLock";
  private static final String TEST_DELETE_TOKEN = "delete_token";
  private static final Sequencer TEST_SEQUENCER = Sequencer.newBuilder()
      .setLockName(TEST_LOCK_NAME)
      .build();

  private CliStateSingleton cliState;

  @BeforeEach
  public void beforeEach() {
    cliState = new CliStateSingleton();
  }

  @Test
  public void itShouldReturnEmptyOptionalOnUnsetGets() {
    assertThat(cliState.getDeleteToken(TEST_URL, TEST_LOCK_NAME)).isEmpty();
    assertThat(cliState.getLockSequencer(TEST_URL,TEST_LOCK_NAME)).isEmpty();
  }

  @Test
  public void itShouldReturnEmptyOptionalOnUnsetRemoves() {
    assertThat(cliState.removeDeleteToken(TEST_URL, TEST_LOCK_NAME)).isEmpty();
    assertThat(cliState.removeLockSequencer(TEST_URL, TEST_LOCK_NAME)).isEmpty();
  }

  @Test
  public void itShouldSetADeleteToken() {
    cliState.setDeleteToken(TEST_URL, TEST_LOCK_NAME, TEST_DELETE_TOKEN);
    assertThat(cliState.getDeleteToken(TEST_URL, TEST_LOCK_NAME)).contains(TEST_DELETE_TOKEN);
  }

  @Test
  public void itShouldSetALockSequencer() {
    cliState.setLockSequencer(TEST_URL, TEST_LOCK_NAME, TEST_SEQUENCER);
    assertThat(cliState.getLockSequencer(TEST_URL, TEST_LOCK_NAME)).contains(TEST_SEQUENCER);
  }

  @Test
  public void itShouldRemoveDeleteToken() {
    cliState.setDeleteToken(TEST_URL, TEST_LOCK_NAME, TEST_DELETE_TOKEN);
    assertThat(cliState.removeDeleteToken(TEST_URL, TEST_LOCK_NAME)).contains(TEST_DELETE_TOKEN);
    assertThat(cliState.getDeleteToken(TEST_URL, TEST_LOCK_NAME)).isEmpty();
  }

  @Test
  public void itShouldRemoveLockSequencer() {
    cliState.setLockSequencer(TEST_URL, TEST_LOCK_NAME, TEST_SEQUENCER);
    assertThat(cliState.removeLockSequencer(TEST_URL, TEST_LOCK_NAME)).contains(TEST_SEQUENCER);
    assertThat(cliState.getLockSequencer(TEST_URL, TEST_LOCK_NAME)).isEmpty();
  }
}
