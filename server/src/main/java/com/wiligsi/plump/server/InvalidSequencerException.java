package com.wiligsi.plump.server;

/**
 * Thrown when a sequencer provided for a Lock has either expired or was not issued by the Lock.
 *
 * @author Steven Miller
 */
public class InvalidSequencerException extends Exception {

  /**
   * Constructs an InvalidSequencerException for the specified lock.
   *
   * @param lockName - the name of the lock the sequencer was sent to
   */
  public InvalidSequencerException(String lockName) {
    super(
        String.format("Provided sequencer for lock named '%s' is invalid", lockName)
    );
  }
}
