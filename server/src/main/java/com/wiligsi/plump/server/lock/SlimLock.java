package com.wiligsi.plump.server.lock;

import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import com.wiligsi.plump.server.InvalidSequencerException;
import com.wiligsi.plump.server.SequencerUtil;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * This 'SlimLock' is used to represent a lock that is lockable by anyone with a claim whenever it
 * is available. This is closer to the traditional locking model than the sequencer model provided
 * by a normal PlumpLock.
 *
 * @author Steven Miller
 */
public class SlimLock extends PlumpLock {

  private static final Logger LOG = Logger.getLogger(SlimLock.class.getName());

  public SlimLock(LockName name, String digestAlgorithm, Duration keepAliveInterval) {
    super(name, digestAlgorithm, keepAliveInterval);
  }

  public SlimLock(String name) throws IllegalArgumentException {
    super(name);
  }

  public SlimLock(LockName name) {
    super(name);
  }

  /**
   * The acquire method for the SlimLock works for any valid Sequencer.
   *
   * @param request - the sequencer used to acquire the lock
   * @return true if the lock was acquired and false if it wasn't
   * @throws InvalidSequencerException - if the sequencer passed to the SlimLock is invalid
   */
  @Override
  public boolean acquire(Sequencer request) throws InvalidSequencerException {
    validateSequencer(request);
    LOG.info(
        String.format(
            "Lock{%s}: Attempting to acquire lock with sequencer '%d'",
            request.getLockName(),
            request.getSequenceNumber()
        )
    );
    AtomicBoolean acquired = new AtomicBoolean(false);
    state.updateAndGet(state -> {
      pruneSequencers();
      final Optional<Sequencer> serverCopy = getServerCopy(request.getSequenceNumber());

      if (state == LockState.UNLOCKED
          && serverCopy.isPresent()
          && SequencerUtil.checkSequencer(request, serverCopy.get())) {
        try {
          SequencerUtil.verifySequencer(request, serverCopy.get(), digestAlgorithm);
          acquired.set(true);
          headSequenceNumber.set(serverCopy.get().getSequenceNumber());
          LOG.info(
              String.format(
                  "Lock{%s}: Acquired with sequencer '%d'",
                  request.getLockName(),
                  request.getSequenceNumber()
              )
          );
          return LockState.LOCKED;
        } catch (InvalidSequencerException sequencerException) {
          LOG.severe("verification exception when attempting to acquire lock!");
          LOG.severe(sequencerException.getMessage());
        }
      }
      return state;
    });
    return acquired.get();
  }

  private Optional<Sequencer> getServerCopy(int sequenceNumber) {
    return Optional.ofNullable(sequencers.get(sequenceNumber));
  }

}
