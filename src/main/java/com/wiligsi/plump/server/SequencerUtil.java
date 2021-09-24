package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpOuterClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

public class SequencerUtil {

    private SequencerUtil() {

    }

    public static Instant getExpirationInstant(PlumpOuterClass.Sequencer sequencer) {
        return Instant.ofEpochMilli(sequencer.getExpiration());
    }

    public static boolean isExpired(PlumpOuterClass.Sequencer sequencer, Instant effectiveTime) {
        return effectiveTime.isAfter(getExpirationInstant(sequencer));
    }

    public static boolean verifySequencer(
            PlumpOuterClass.Sequencer request,
            PlumpOuterClass.Sequencer local
    ) throws NoSuchAlgorithmException {
        final String hashedRequestKey = hashKey(request.getKey());
        return request.getLockName().equals(local.getLockName()) &&
                request.getSequenceNumber() == local.getSequenceNumber() &&
                hashedRequestKey.equals(local.getKey()) &&
                request.getExpiration() == local.getExpiration();
    }

    public static String hashKey(String key) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA3-256");
        final byte[] hashedBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(hashedBytes);
    }

}
