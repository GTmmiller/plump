package com.wiligsi.plump.common;

public class InvalidSequencerException extends Exception {

  public InvalidSequencerException(String lockName) {
    super(
        String.format("Provided sequencer for lock named '%s' is invalid", lockName)
    );
  }
}
