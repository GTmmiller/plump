package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpOuterClass.Sequencer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public class SequencerUtil {

    private SequencerUtil() {

    }

    public static Instant getExpirationInstant(Sequencer sequencer) {
        return Instant.ofEpochMilli(sequencer.getExpiration());
    }

    public static boolean isExpired(Sequencer sequencer, Instant effectiveTime) {
        return effectiveTime.isAfter(getExpirationInstant(sequencer));
    }

    public static boolean checkSequencer(Sequencer request, Sequencer local) {
        return request.getLockName().equals(local.getLockName()) &&
            request.getSequenceNumber() == local.getSequenceNumber();
    }

    public static void verifySequencer(
            Sequencer request,
            Sequencer local,
            MessageDigest digest
    ) throws InvalidSequencerException {
        final String hashedRequestKey = hashKey(request.getKey(), digest);
        boolean validSequencer = checkSequencer(request, local) &&
                hashedRequestKey.equals(local.getKey()) &&
                request.getExpiration() == local.getExpiration();

        if (!validSequencer) {
            throw new InvalidSequencerException(local.getLockName());
        }
    }

    public static String hashKey(String key, MessageDigest digest) {
        final byte[] hashedBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(hashedBytes);
    }

}
