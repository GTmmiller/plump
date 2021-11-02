package com.wiligsi.plump.server.assertion;

import static com.wiligsi.plump.PlumpOuterClass.*;

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
}
