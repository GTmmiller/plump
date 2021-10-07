package com.wiligsi.plump.server.matcher;

import io.grpc.StatusRuntimeException;
import org.assertj.core.api.InstanceOfAssertFactories;

public class PlumpAssertions implements InstanceOfAssertFactories {
    protected PlumpAssertions () {}

    public static StatusRuntimeExceptionAssert assertThat(StatusRuntimeException actual) {
        return new StatusRuntimeExceptionAssert(actual);
    }
}
