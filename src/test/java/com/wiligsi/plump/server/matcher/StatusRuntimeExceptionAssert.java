package com.wiligsi.plump.server.matcher;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.AbstractThrowableAssert;

public class StatusRuntimeExceptionAssert extends AbstractThrowableAssert<StatusRuntimeExceptionAssert, StatusRuntimeException> {
    public StatusRuntimeExceptionAssert(StatusRuntimeException actual) {
        super(actual, StatusRuntimeExceptionAssert.class);
    }


    public StatusRuntimeExceptionAssert isInvalidLockNameException() {
        isInstanceOf(StatusRuntimeException.class);
        hasMessageContaining("Names should be a series of 4-12 alphanumeric characters");
        hasFieldOrProperty("status");
        extracting("status").hasFieldOrPropertyWithValue("code", Status.INVALID_ARGUMENT.getCode());

        return this;
    }
}
