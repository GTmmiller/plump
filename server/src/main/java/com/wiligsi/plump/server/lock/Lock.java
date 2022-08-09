package com.wiligsi.plump.server.lock;

import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import com.wiligsi.plump.server.InvalidSequencerException;
import java.time.Duration;
import java.util.Optional;

/**
 * This interface represents actions plump expects a lock to be able to perform.
 *
 * @author Steven Miller
 */
public interface Lock {

  Duration getKeepAliveInterval();

  boolean acquire(Sequencer request) throws InvalidSequencerException;

  boolean release(Sequencer request) throws InvalidSequencerException;

  Sequencer createSequencer();

  void revokeSequencer(Sequencer request) throws InvalidSequencerException;

  Sequencer keepAlive(Sequencer sequencer) throws InvalidSequencerException;

  LockName getName();

  Optional<Integer> getHeadSequencerNumber();

  int getNextSequenceNumber();

  LockState getState();
}
