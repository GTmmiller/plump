package com.wiligsi.plump.server.assertion;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.AbstractThrowableAssert;

public class StatusRuntimeExceptionAssert extends
    AbstractThrowableAssert<StatusRuntimeExceptionAssert, StatusRuntimeException> {

  public StatusRuntimeExceptionAssert(StatusRuntimeException actual) {
    super(actual, StatusRuntimeExceptionAssert.class);
  }

  public StatusRuntimeExceptionAssert isInvalidLockNameException() {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("Names should be a series of 4-12 alphanumeric characters");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.INVALID_ARGUMENT.getCode()
        );

    return this;
  }

  public StatusRuntimeExceptionAssert isLockNameAlreadyExistsExceptionFor(String lockName) {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("com.wiligsi.plump.server.lock.Lock named");
    hasMessageContaining(lockName);
    hasMessageContaining("already exists");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.ALREADY_EXISTS.getCode()
        );
    return this;
  }

  public StatusRuntimeExceptionAssert isLockNameNotFoundExceptionFor(String lockName) {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("com.wiligsi.plump.server.lock.Lock named");
    hasMessageContaining(lockName);
    hasMessageContaining("does not exist");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.NOT_FOUND.getCode()
        );
    return this;
  }

  public StatusRuntimeExceptionAssert isInvalidSequencerExceptionFor(String lockName) {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("Provided sequencer for lock named");
    hasMessageContaining(lockName);
    hasMessageContaining("is invalid");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.INVALID_ARGUMENT.getCode()
        );
    return this;
  }

  public StatusRuntimeExceptionAssert isInvalidDestroyKeyExceptionFor(String lockName) {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("Cannot destroy lock");
    hasMessageContaining(lockName);
    hasMessageContaining("Destroy key is invalid");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.INVALID_ARGUMENT.getCode()
        );
    return this;
  }

  public StatusRuntimeExceptionAssert isNullSequencerException() {
    isInstanceOf(StatusRuntimeException.class);
    hasMessageContaining("Passed sequencer is null");
    hasFieldOrProperty("status");
    extracting("status")
        .hasFieldOrPropertyWithValue(
            "code",
            Status.INVALID_ARGUMENT.getCode()
        );
    return this;
  }
}
