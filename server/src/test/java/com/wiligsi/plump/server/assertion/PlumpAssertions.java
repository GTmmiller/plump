package com.wiligsi.plump.server.assertion;

import static com.wiligsi.plump.common.PlumpOuterClass.*;

import com.wiligsi.plump.server.Lock;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.InstanceOfAssertFactories;

public class PlumpAssertions implements InstanceOfAssertFactories {

  protected PlumpAssertions() {
  }

  public static StatusRuntimeExceptionAssert assertThat(StatusRuntimeException actual) {
    return new StatusRuntimeExceptionAssert(actual);
  }

  public static SequencerAssert assertThat(Sequencer actual) {
    return new SequencerAssert(actual);
  }

  public static LockAssert assertThat(Lock actual) {
    return new LockAssert(actual);
  }
}
