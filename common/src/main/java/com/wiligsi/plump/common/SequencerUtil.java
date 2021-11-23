package com.wiligsi.plump.common;

import com.wiligsi.plump.PlumpOuterClass.Sequencer;
import java.security.MessageDigest;
import java.time.Instant;

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
    return request.getLockName().equals(local.getLockName())
        && request.getSequenceNumber() == local.getSequenceNumber();
  }

  public static void verifySequencer(
      Sequencer request,
      Sequencer local,
      MessageDigest digest
  ) throws InvalidSequencerException {
    final String hashedRequestKey = KeyUtil.hashKey(request.getKey(), digest);
    boolean validSequencer = checkSequencer(request, local)
        && hashedRequestKey.equals(local.getKey())
        && request.getExpiration() == local.getExpiration();

    if (!validSequencer) {
      throw new InvalidSequencerException(local.getLockName());
    }
  }
}
