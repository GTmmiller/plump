package com.wiligsi.plump.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static com.wiligsi.plump.PlumpOuterClass.*;

public class Lock {
    private static final Logger LOG = Logger.getLogger(PlumpServer.class.getName());

    private static final String DEFAULT_DIGEST_ALGORITHM = "SHA3-256";

    private static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofMinutes(2);

    private final LockName name;
    private final BlockingQueue<Integer> sequenceNumbers;
    private final ConcurrentMap<Integer, Sequencer> sequencers;
    private final SecureRandom secureRandom;
    private final AtomicInteger nextSequenceNumber;
    private final MessageDigest digest;
    private final Duration keepAliveInterval;
    private final AtomicReference<LockState> state;

    private Clock clock;

    // TODO: Make some kind of 'check ownership' method with the lock

    public Lock(LockName name,  MessageDigest digest, Duration keepAliveInterval) throws IllegalArgumentException {
        this.name = name;
        this.digest = digest;
        this.keepAliveInterval = keepAliveInterval;
        this.clock = Clock.systemDefaultZone();
        this.sequenceNumbers = new LinkedBlockingQueue<>();
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
                if (state == LockState.UNLOCKED &&
                        head.isPresent() &&
                        SequencerUtil.checkSequencer(request, head.get())) {
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
            if (state == LockState.LOCKED &&
                    head.isPresent() &&
                    SequencerUtil.checkSequencer(request, head.get())) {
                try {
                    SequencerUtil.verifySequencer(request, head.get(), digest);
                    sequencers.remove(head.get().getSequenceNumber());
                    sequenceNumbers.remove();
                    return LockState.UNLOCKED;
                } catch (InvalidSequencerException sequencerException) {
                    LOG.severe("verification exception when attempting to release lock!");
                    LOG.severe(sequencerException.getMessage());
                }
            }
            return state;
        });
        return updatedState == LockState.UNLOCKED && updatedState != originalState;
    }

    public Sequencer createSequencer() {
        // get the params for the sequencer
        final Instant nextSequencerExpiration = Instant.now(clock).plus(keepAliveInterval);
        final int nextSequencerNumber = nextSequenceNumber.getAndIncrement();
        final String nextSequencerKey = generateRandomKey();
        final String keyHash = SequencerUtil.hashKey(nextSequencerKey, digest);

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

        sequenceNumbers.add(nextSequencerNumber);

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
        final String newSequencerKey = generateRandomKey();
        final String newSequencerKeyHash = SequencerUtil.hashKey(newSequencerKey, digest);
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

        if (state.get() == LockState.LOCKED &&
                (head.isEmpty() ||
                SequencerUtil.isExpired(head.get(), effectiveTime))
        ) {
            state.set(LockState.UNLOCKED);
        }

        Optional<Integer> removedSequenceNumber;
        do {
            removedSequenceNumber = pruneHead(effectiveTime);
        } while (removedSequenceNumber.isPresent());
    }

    protected Optional<Integer> pruneHead(Instant effectiveTime) {
        final Optional<Sequencer> head = getHead();

        if (head.isPresent() && SequencerUtil.isExpired(head.get(), effectiveTime)) {
            Integer headSequenceNumber = head.get().getSequenceNumber();
            sequencers.remove(headSequenceNumber);
            sequenceNumbers.remove();
            return Optional.of(headSequenceNumber);
        }

        return Optional.empty();
    }

    protected String generateRandomKey() {
        byte[]  keyBytes = new byte[24];
        secureRandom.nextBytes(keyBytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(keyBytes);
    }

    protected Optional<Sequencer> getHead() {
        Integer headSequenceNumber = sequenceNumbers.peek();
        if (headSequenceNumber == null) {
            return Optional.empty();
        } else {
            return Optional.of(sequencers.get(headSequenceNumber));
        }
    }

    protected void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String toString() {
        return "Lock{" +
                "name='" + name + '\'' +
                ", sequencers=" + sequencers +
                ", state=" + state +
                '}';
    }
}
