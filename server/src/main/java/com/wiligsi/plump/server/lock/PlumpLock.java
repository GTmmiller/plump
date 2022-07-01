package com.wiligsi.plump.server.lock;

import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;

import com.wiligsi.plump.server.InvalidSequencerException;
import com.wiligsi.plump.server.KeyUtil;
import com.wiligsi.plump.server.SequencerUtil;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * The most important class for the Plump server. The Lock provides functionality for creating
 * sequencers, locking and unlocking, and sequencer verification.
 *
 * <p>Locks keep track of a map of sequencers. A sequencer is created with the createSequencer
 * method. This gives you a sequencer and puts you "in line" for the server. Whenever an action is
 * performed on a lock, expired sequencers are removed and the lock can be unlocked if the current
 * locking sequencer is removed. The sequencer check only checks as needed which makes a denial of
 * service less of a problem.</p>
 *
 * <p>Once you've acquired a sequencer you can then acquire a lock. Only the first in line
 * sequencer
 * will successfully acquire a lock. Utility methods like getHeadSequencerNumber and
 * getNextSequenceNumber allow clients to make decisions on when to try to acquire the lock.
 * Sequencers must call the keepAlive method periodically if they aren't being used. All methods
 * that require a Sequencer on this class will also keep the sequencer alive.</p>
 *
 * <p>When the lock is no longer needed, it can be released with the release method. Once the lock
 * is acquired, the acquiring Sequencer must be kept alive or the lock will unlock due to Sequencer
 * timeout. Sequencers are retired when they are used to release a lock</p>
 *
 * @author Steven Miller
 */
public class PlumpLock implements Lock {

  private static final Logger LOG = Logger.getLogger(PlumpLock.class.getName());

  private static final String DEFAULT_DIGEST_ALGORITHM = "SHA3-256";

  private static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofMinutes(2);

  protected final LockName name;
  protected final ConcurrentMap<Integer, Sequencer> sequencers;
  protected final SecureRandom secureRandom;
  protected final AtomicInteger nextSequenceNumber;
  protected final AtomicInteger headSequenceNumber;
  protected final String digestAlgorithm;
  protected final Duration keepAliveInterval;
  protected final AtomicReference<LockState> state;

  private Clock clock;

  /**
   * Constructs a Lock given a name, a digest algorithm (sha-256, etc), and a keepAliveInterval for
   * sequencers. Fails if a string instance of a SecureRandom number generator cannot be acquired
   *
   * @param name              - the name of the lock
   * @param digestAlgorithm   - the digest algorithm used to create sequencer random keys
   * @param keepAliveInterval - the amount of time a sequencer is valid for before it's updated
   */
  public PlumpLock(LockName name, String digestAlgorithm, Duration keepAliveInterval) {
    this.name = name;
    this.digestAlgorithm = digestAlgorithm;
    this.keepAliveInterval = keepAliveInterval;
    this.clock = Clock.systemDefaultZone();
    this.headSequenceNumber = new AtomicInteger(0);
    this.sequencers = new ConcurrentHashMap<>();
    this.state = new AtomicReference<>(LockState.UNLOCKED);
    this.nextSequenceNumber = new AtomicInteger();
    try {
      secureRandom = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException algorithmException) {
      LOG.severe("Could not get a secure random instance!");
      throw new RuntimeException(algorithmException);
    }

    LOG.fine("Created new lock: " + this);
  }

  /**
   * Construct a Lock given a String name.
   *
   * @param name - the Lock's name
   * @throws IllegalArgumentException - if the name is invalid
   */
  public PlumpLock(String name) throws IllegalArgumentException {
    this(new LockName(name));
  }

  /**
   * Construct a Lock given a LockName object.
   *
   * @param name - the LockName object for the new lock
   */
  public PlumpLock(LockName name) {
    this(name, DEFAULT_DIGEST_ALGORITHM, DEFAULT_KEEP_ALIVE_INTERVAL);
  }

  /**
   * Returns how long a Lock's Sequencer can go without being used or renewed.
   *
   * @return - the keepAliveInternal
   */
  @Override
  public Duration getKeepAliveInterval() {
    return keepAliveInterval;
  }

  /**
   * Attempt to acquire the lock with the passed in sequencer. A lock can only be acquired if the
   * Sequencer is the same sequencer being pointed to by the headSequencerNumber value and the lock
   * is in the UNLOCKED state. When successful, this method changes the Lock's state to LOCKED.
   *
   * @param request - the sequencer used to acquire the lock
   * @return true if the lock was acquired successfully, otherwise false
   * @throws InvalidSequencerException - if the passed in sequencer has expired or does not exist
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
      final Optional<Sequencer> head = getHead();
      if (state == LockState.UNLOCKED
          && head.isPresent()
          && SequencerUtil.checkSequencer(request, head.get())) {
        try {
          SequencerUtil.verifySequencer(request, head.get(), digestAlgorithm);
          acquired.set(true);
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

  /**
   * Attempt to release the lock with the passed in sequencer. A lock can only be released if the
   * Sequencer is the same sequencer being pointed to by the headSequencerNumber value and the lock
   * is in the LOCKED state. When successful, this method changes the Lock's state to UNLOCKED and
   * removes the passed in Sequencer from the Lock.
   *
   * @param request - the Sequencer used to release the lock
   * @return true if the Lock is released and false if it is not
   * @throws InvalidSequencerException - if the passed in Sequencer has expired or does not exist
   */
  @Override
  public boolean release(Sequencer request) throws InvalidSequencerException {
    validateSequencer(request);
    LOG.info(
        String.format(
            "Lock{%s}: Attempting to release lock with sequencer '%d'",
            request.getLockName(),
            request.getSequenceNumber()
        )
    );

    return internalReleaseLock(request);
  }

  /**
   * Creates a new sequencer for this lock. This sequencer will have a new number corresponding to
   * the nextSequencerNumber variable.
   *
   * @return a new sequencer for the lock
   */
  @Override
  public Sequencer createSequencer() {
    final Instant nextSequencerExpiration = Instant.now(clock).plus(keepAliveInterval);
    final String nextSequencerKey = KeyUtil.generateRandomKey(secureRandom);
    final String keyHash = hashKey(nextSequencerKey);
    final int nextSequencerNumber = nextSequenceNumber.getAndIncrement();

    final Sequencer partialSequencer = Sequencer.newBuilder()
        .setLockName(name.getDisplayName())
        .setSequenceNumber(nextSequencerNumber)
        .setExpiration(nextSequencerExpiration.toEpochMilli())
        .buildPartial();

    // Store locally with a hash
    sequencers.put(
        nextSequencerNumber,
        Sequencer.newBuilder(partialSequencer)
            .setKey(keyHash)
            .build()
    );

    // Return new sequencer
    return Sequencer.newBuilder(partialSequencer)
        .setKey(nextSequencerKey)
        .build();
  }

  @Override
  public void revokeSequencer(Sequencer request) throws InvalidSequencerException {
    validateSequencer(request);
    LOG.info(
        String.format(
            "Lock{%s}: Attempting to revoke sequencer '%d'",
            request.getLockName(),
            request.getSequenceNumber()
        )
    );

    if (!internalReleaseLock(request)) {
      sequencers.remove(request.getSequenceNumber());
    }
  }

  /**
   * Extend a sequencer's expiration time to the current time plus the keepAliveInterval.
   *
   * <p>This is done instead of adding the keepAliveInterval to the expiration time to prevent a
   * user from creating a sequencer with a far future expiration time.</p>
   *
   * @param sequencer - the Sequencer that will have its expiration time extended
   * @return an updated version of the passed in Sequencer with a new expiration time
   * @throws InvalidSequencerException if the passed in Sequencer is invalid
   */
  @Override
  public Sequencer keepAlive(Sequencer sequencer) throws InvalidSequencerException {
    Instant effectiveTime = Instant.now(clock);
    validateSequencer(sequencer);

    final Sequencer localSequencer = sequencers.get(sequencer.getSequenceNumber());
    SequencerUtil.verifySequencer(sequencer, localSequencer, digestAlgorithm);

    // Update the sequencer and return the new one
    final String newSequencerKey = KeyUtil.generateRandomKey(secureRandom);
    final String newSequencerKeyHash = hashKey(newSequencerKey);
    final Sequencer newLocalSequencer = Sequencer.newBuilder(localSequencer)
        .setExpiration(effectiveTime.plus(keepAliveInterval).toEpochMilli())
        .setKey(newSequencerKeyHash)
        .build();
    sequencers.put(newLocalSequencer.getSequenceNumber(), newLocalSequencer);
    return Sequencer.newBuilder(newLocalSequencer)
        .setKey(newSequencerKey)
        .build();
  }

  /**
   * Returns the LockName object corresponding to the Lock.
   *
   * @return the Lock's LockName object
   */
  @Override
  public LockName getName() {
    return name;
  }

  /**
   * Returns the current head sequencer number. This can be used by clients to determine how close
   * they are to being able to acquire the lock. It can also be used in conjunction with the lock
   * status to know which sequencer has acquired the lock.
   *
   * @return the head sequencer number if it exists or an empty optional if there are no Sequencers
   */
  @Override
  public Optional<Integer> getHeadSequencerNumber() {
    pruneSequencers();
    Optional<Sequencer> head = getHead();
    return head.map(Sequencer::getSequenceNumber);
  }

  /**
   * Returns the number the next generated sequencer will have. Can be used to gauge how long a user
   * might have to wait in line to acquire the lock.
   *
   * @return the sequence number the next created Sequencer will have
   */
  @Override
  public int getNextSequenceNumber() {
    return nextSequenceNumber.get();
  }

  /**
   * Returns the current state of the Lock. This can be used to determine if it's worth trying to
   * acquire the Lock along with other information provided by the Lock.
   *
   * @return UNLOCKED or LOCKED depending on the current state of the Lock
   */
  @Override
  public LockState getState() {
    return state.get();
  }

  protected void validateSequencer(Sequencer sequencer) throws InvalidSequencerException {
    if (!sequencers.containsKey(sequencer.getSequenceNumber())) {
      throw new InvalidSequencerException(name.getDisplayName());
    }

    final Sequencer localSequencer = sequencers.get(sequencer.getSequenceNumber());

    if (!SequencerUtil.checkSequencer(sequencer, localSequencer)) {
      throw new InvalidSequencerException(name.getDisplayName());
    }
  }

  protected void pruneSequencers() {
    Instant effectiveTime = Instant.now(clock);
    final Optional<Sequencer> head = getHead();

    if (state.get() == LockState.LOCKED
        && (head.isEmpty() || SequencerUtil.isExpired(head.get(), effectiveTime))
    ) {
      state.set(LockState.UNLOCKED);
    }

    Optional<Sequencer> removedSequencer;
    do {
      removedSequencer = pruneHead(effectiveTime);
    } while (removedSequencer.isPresent());
  }

  protected Optional<Sequencer> pruneHead(Instant effectiveTime) {
    final int oldHead = headSequenceNumber.get();
    final int newHead = headSequenceNumber.updateAndGet(headNumber -> {
      final Sequencer head = sequencers.get(headNumber);
      if (head != null && SequencerUtil.isExpired(head, effectiveTime)) {
        return headNumber + 1;
      }
      return headNumber;
    });

    if (newHead > oldHead) {
      final Sequencer removeHead = sequencers.remove(oldHead);
      if (removeHead != null) {
        return Optional.of(removeHead);
      }
    }
    return Optional.empty();
  }

  protected String hashKey(String key) {
    return KeyUtil.hashKey(key, digestAlgorithm);
  }

  protected Optional<Sequencer> getHead() {
    final Sequencer headSequencer = sequencers.get(headSequenceNumber.get());
    if (headSequencer == null) {
      return Optional.empty();
    } else {
      return Optional.of(headSequencer);
    }
  }

  protected void setClock(Clock clock) {
    this.clock = clock;
  }

  protected boolean internalReleaseLock(Sequencer request) {
    final AtomicBoolean released = new AtomicBoolean(false);
    state.updateAndGet(state -> {
      pruneSequencers();
      final Optional<Sequencer> head = getHead();
      if (state == LockState.LOCKED
          && head.isPresent()
          && SequencerUtil.checkSequencer(request, head.get())) {
        try {
          SequencerUtil.verifySequencer(request, head.get(), digestAlgorithm);
          released.set(true);
          LOG.info(
              String.format(
                  "Lock{%s}: Released with sequencer '%d'",
                  request.getLockName(),
                  request.getSequenceNumber()
              )
          );
          return LockState.UNLOCKED;
        } catch (InvalidSequencerException sequencerException) {
          LOG.severe("verification exception when attempting to release lock!");
          LOG.severe(sequencerException.getMessage());
        }
      }
      return state;
    });

    if (released.get()) {
      final int oldHead = headSequenceNumber.getAndIncrement();
      sequencers.remove(oldHead);
      LOG.info(
          String.format(
              "Lock{%s}: Old sequencer '%d' removed from head",
              request.getLockName(),
              oldHead
          )
      );
    }

    return released.get();
  }

  @Override
  public String toString() {
    return String.format(
        "com.wiligsi.plump.server.lock.Lock{name='%s', sequencers=%s, state=%s}",
        name, sequencers, state
    );
  }
}
