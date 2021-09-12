package com.wiligsi.plump.server;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedList;
import java.util.stream.Collectors;

import static com.wiligsi.plump.PlumpOuterClass.*;

public class Lock {
    final private String name;
    private LinkedList<Sequencer> sequencers;
    private LockState state;
    final private SecureRandom secureRandom;

    public Lock(String name) throws NoSuchAlgorithmException {
        this.name = name;
        this.sequencers = new LinkedList<>();
        this.state = LockState.UNLOCKED;
        secureRandom = SecureRandom.getInstanceStrong();
    }

    public boolean lock(Sequencer request) throws NoSuchAlgorithmException {
        pruneSequencers();
        if (state == LockState.UNLOCKED && SequencerUtil.verifySequencer(request, sequencers.getFirst())) {
            state = LockState.LOCKED;
            return true;
        }
        return false;
    }

    public boolean unlock(Sequencer request) throws NoSuchAlgorithmException {
        pruneSequencers();
        if (state == LockState.LOCKED && SequencerUtil.verifySequencer(request, sequencers.getFirst())) {
            state = LockState.UNLOCKED;
            return true;
        }
        return false;
    }

    public Sequencer createSequencer() throws NoSuchAlgorithmException {
        // get the params for the sequencer
        final Instant nextSequencerExpiration = Instant.now().plus(Duration.ofMinutes(2));
        final int nextSequencerNumber = sequencers.getLast().getSequenceNumber() + 1;
        final String nextSequencerKey = generateRandomKey();
        final String keyHash = SequencerUtil.hashKey(nextSequencerKey);

        final Sequencer partialSequencer = Sequencer.newBuilder()
                .setLockName(name)
                .setSequenceNumber(nextSequencerNumber)
                .setExpiration(nextSequencerExpiration.toEpochMilli())
                .buildPartial();

        // Store locally with a hash
        sequencers.add(
                Sequencer.newBuilder(partialSequencer)
                        .setKey(keyHash)
                        .build()
        );

        // Return new sequencer
        return Sequencer.newBuilder(partialSequencer)
                .setKey(nextSequencerKey)
                .build();
    }

    public String getName() {
        return name;
    }

    public Integer getNextSequencerNumber() {
        pruneSequencers();
        return sequencers.getFirst().getSequenceNumber();
    }

    protected void pruneSequencers() {
        Instant effectiveTime = Instant.now();
        final Sequencer head = sequencers.getFirst();

        if (state == LockState.LOCKED && SequencerUtil.isExpired(head, effectiveTime)) {
            state = LockState.UNLOCKED;

            sequencers = sequencers.stream().dropWhile(
                    sequencer -> SequencerUtil.getExpirationInstant(sequencer).isBefore(effectiveTime)
            ).collect(Collectors.toCollection(LinkedList::new));
        }
    }

    protected String generateRandomKey() {
        byte[]  keyBytes = new byte[24];
        secureRandom.nextBytes(keyBytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(keyBytes);
    }
}
