package com.wiligsi.plump.server.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.server.lock.Lock;
import com.wiligsi.plump.server.lock.LockState;
import org.assertj.core.api.AbstractObjectAssert;

public class LockAssert extends AbstractObjectAssert<LockAssert, Lock> {
  public LockAssert(Lock lock) {
    super(lock, LockAssert.class);
  }

  public LockAssert isUnlocked() {
    assertThat(this.actual.getState()).isEqualTo(LockState.UNLOCKED);

    return this;
  }

  public LockAssert isLocked() {
    assertThat(this.actual.getState()).isEqualTo(LockState.LOCKED);

    return this;
  }
}
