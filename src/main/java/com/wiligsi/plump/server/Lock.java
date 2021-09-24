package com.wiligsi.plump.server;

import com.google.common.annotations.VisibleForTesting;

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
import java.util.logging.Logger;

import static com.wiligsi.plump.PlumpOuterClass.*;

public class Lock {
    private static final Logger LOG = Logger.getLogger(PlumpServer.class.getName());

    final private String name;
    private final BlockingQueue<Integer> sequenceNumbers;
    private final ConcurrentMap<Integer, Sequencer> sequencers;
    private LockState state;
    final private SecureRandom secureRandom;
    final private AtomicInteger nextSequenceNumber;
    private Clock clock;

    public Lock(String name) throws NoSuchAlgorithmException {
        this.name = name;
        this.clock = Clock.systemDefaultZone();
        this.sequenceNumbers = new LinkedBlockingQueue<>();
        this.sequencers = new ConcurrentHashMap<>();
        this.state = LockState.UNLOCKED;
        this.nextSequenceNumber = new AtomicInteger();
        secureRandom = SecureRandom.getInstanceStrong();
        LOG.info("Created new lock: " + this);
    }

    public boolean lock(Sequencer request) throws NoSuchAlgorithmException {
        pruneSequencers();
        final Optional<Sequencer> head = getHead();
        if (state == LockState.UNLOCKED &&
                head.isPresent() &&
                SequencerUtil.verifySequencer(request, head.get())) {
            state = LockState.LOCKED;
            return true;
        }
        return false;
    }

    public boolean unlock(Sequencer request) throws NoSuchAlgorithmException {
        pruneSequencers();
        final Optional<Sequencer> head = getHead();

        if (state == LockState.LOCKED &&
                head.isPresent() &&
                SequencerUtil.verifySequencer(request, head.get())) {
            sequencers.remove(head.get().getSequenceNumber());
            sequenceNumbers.remove();

            state = LockState.UNLOCKED;
            return true;
        }
        return false;
    }

    public Sequencer createSequencer() throws NoSuchAlgorithmException {
        // get the params for the sequencer
        final Instant nextSequencerExpiration = Instant.now(clock).plus(Duration.ofMinutes(2));
        final int nextSequencerNumber = nextSequenceNumber.getAndIncrement();
        final String nextSequencerKey = generateRandomKey();
        final String keyHash = SequencerUtil.hashKey(nextSequencerKey);

        final Sequencer partialSequencer = Sequencer.newBuilder()
                .setLockName(name)
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

    public Optional<Sequencer> keepAlive(Sequencer sequencer) throws NoSuchAlgorithmException {
        Instant effectiveTime = Instant.now(clock);

        // retrieve sequencer from map
        if (sequencers.containsKey(sequencer.getSequenceNumber())) {
            final Sequencer localSequencer = sequencers.get(sequencer.getSequenceNumber());
            if (SequencerUtil.verifySequencer(sequencer, localSequencer)) {
                // Update the sequencer and return the new one
                final String newSequencerKey = generateRandomKey();
                final String newSequencerKeyHash = SequencerUtil.hashKey(newSequencerKey);
                final Sequencer newLocalSequencer = Sequencer.newBuilder(localSequencer)
                        .setExpiration(effectiveTime.plus(Duration.ofMinutes(2)).toEpochMilli())
                        .setKey(newSequencerKeyHash)
                        .build();
                sequencers.put(newLocalSequencer.getSequenceNumber(), newLocalSequencer);
                return Optional.of(
                        Sequencer.newBuilder(newLocalSequencer)
                        .setKey(newSequencerKey)
                        .build()
                );
            }
        }
        return Optional.empty();
    }

    public String getName() {
        return name;
    }

    public Optional<Integer> getHeadSequencerNumber() {
        pruneSequencers();
        Optional<Sequencer> head = getHead();
        return head.map(Sequencer::getSequenceNumber);
    }

    public LockState getState() {
        return state;
    }

    protected void pruneSequencers() {
        Instant effectiveTime = Instant.now(clock);
        final Optional<Sequencer> head = getHead();

        if (state == LockState.LOCKED &&
                (head.isEmpty() ||
                SequencerUtil.isExpired(head.get(), effectiveTime))
        ) {
            state = LockState.UNLOCKED;
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

    @VisibleForTesting
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
