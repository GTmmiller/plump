package com.wiligsi.plump.server.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import com.wiligsi.plump.server.Lock;
import com.wiligsi.plump.server.LockState;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;

public class LockAssert extends AbstractObjectAssert<LockAssert, Lock> {
  public LockAssert(Lock lock) {
    super(lock, LockAssert.class);
  }

  public LockAssert isUnlocked() {
    Assertions.assertThat(this.actual.getState()).isEqualTo(LockState.UNLOCKED);

    return this;
  }

  public LockAssert isLocked() {
    Assertions.assertThat(this.actual.getState()).isEqualTo(LockState.LOCKED);

    return this;
  }
}
