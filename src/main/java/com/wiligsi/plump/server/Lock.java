package com.wiligsi.plump.server;

import static com.wiligsi.plump.PlumpOuterClass.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class Lock {

  private static final Logger LOG = Logger.getLogger(PlumpServer.class.getName());

  private static final String DEFAULT_DIGEST_ALGORITHM = "SHA3-256";

  private static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofMinutes(2);

  private final LockName name;
  private final ConcurrentMap<Integer, Sequencer> sequencers;
  private final SecureRandom secureRandom;
  private final AtomicInteger nextSequenceNumber;
  private final AtomicInteger headSequenceNumber;
  private final MessageDigest digest;
  private final Duration keepAliveInterval;
  private final AtomicReference<LockState> state;

  private Clock clock;

  // TODO: Make some kind of 'check ownership' method with the lock
  // takes in a sequencer. returns true if lock is currently locked and the sequencer is the head
  // Useful for the application checking to make sure that the person who claims to have the lock
  // actually has it

  public Lock(LockName name, MessageDigest digest, Duration keepAliveInterval)
      throws IllegalArgumentException {
    this.name = name;
    this.digest = digest;
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

  public Lock(String name) throws IllegalArgumentException, NoSuchAlgorithmException {
    this(new LockName(name));
  }

  public Lock(LockName name) throws NoSuchAlgorithmException {
    this(name, MessageDigest.getInstance(DEFAULT_DIGEST_ALGORITHM), DEFAULT_KEEP_ALIVE_INTERVAL);
  }

  public Duration getKeepAliveInterval() {
    return keepAliveInterval;
  }

  public boolean acquire(Sequencer request) throws InvalidSequencerException {
    validateSequencer(request);
    pruneSequencers();
    final Optional<Sequencer> head = getHead();
    // Should be an update and get or some kind of compare and update
    final LockState originalState = state.get();
    final LockState updatedState = state.updateAndGet(state -> {
      if (state == LockState.UNLOCKED
          && head.isPresent()
          && SequencerUtil.checkSequencer(request, head.get())) {
        try {
          SequencerUtil.verifySequencer(request, head.get(), digest);
          return LockState.LOCKED;
        } catch (InvalidSequencerException sequencerException) {
          LOG.severe("verification exception when attempting to acquire lock!");
          LOG.severe(sequencerException.getMessage());
        }
      }
      return state;
    });
    return updatedState == LockState.LOCKED && updatedState != originalState;
  }

  public boolean release(Sequencer request) throws InvalidSequencerException {
    validateSequencer(request);
    pruneSequencers();
    final Optional<Sequencer> head = getHead();

    final LockState originalState = state.get();
    final LockState updatedState = state.updateAndGet(state -> {
      if (state == LockState.LOCKED
          && head.isPresent()
          && SequencerUtil.checkSequencer(request, head.get())) {
        try {
          SequencerUtil.verifySequencer(request, head.get(), digest);
          return LockState.UNLOCKED;
        } catch (InvalidSequencerException sequencerException) {
          LOG.severe("verification exception when attempting to release lock!");
          LOG.severe(sequencerException.getMessage());
        }
      }
      return state;
    });

    final boolean unlockSuccessful =
        updatedState == LockState.UNLOCKED && updatedState != originalState;

    if (unlockSuccessful) {
      final int oldHead = headSequenceNumber.getAndIncrement();
      sequencers.remove(oldHead);
    }
    return unlockSuccessful;
  }

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

  public Sequencer keepAlive(Sequencer sequencer) throws InvalidSequencerException {
    Instant effectiveTime = Instant.now(clock);
    validateSequencer(sequencer);

    final Sequencer localSequencer = sequencers.get(sequencer.getSequenceNumber());
    SequencerUtil.verifySequencer(sequencer, localSequencer, digest);

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

  public LockName getName() {
    return name;
  }

  public Optional<Integer> getHeadSequencerNumber() {
    pruneSequencers();
    Optional<Sequencer> head = getHead();
    return head.map(Sequencer::getSequenceNumber);
  }

  public int getNextSequenceNumber() {
    return nextSequenceNumber.get();
  }

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
    return KeyUtil.hashKey(key, digest);
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

  // TODO: Just make this a format string this concat is filthy
  @Override
  public String toString() {
    return "Lock{"
        + "name='"
        + name
        + '\''
        + ", sequencers="
        + sequencers
        + ", state="
        + state
        + '}';
  }
}
