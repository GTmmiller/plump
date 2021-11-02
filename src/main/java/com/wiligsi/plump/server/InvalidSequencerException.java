package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpOuterClass.Sequencer;

public class InvalidSequencerException extends Exception {

  public InvalidSequencerException(String lockName) {
    super(
        String.format("Provided sequencer for lock named '%s' is invalid", lockName)
    );
  }
}
