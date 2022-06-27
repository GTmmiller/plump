package com.wiligsi.plump.server;

import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;

import java.time.Instant;

/**
 * This is a utility class that has some shared methods around Sequencers for things like
 * verification and making dates easier to deal with.
 *
 * @author Steven Miller
 */
public class SequencerUtil {

  private SequencerUtil() {
  }

  /**
   * Convert the expiration date from a uint64 to a java Instant.
   *
   * @param sequencer - the sequencer to get the date from
   * @return the Instant representing the sequence's expiration date
   */
  public static Instant getExpirationInstant(Sequencer sequencer) {
    return Instant.ofEpochMilli(sequencer.getExpiration());
  }

  /**
   * Check if a given sequencer is expired given a current time.
   *
   * @param sequencer     - the sequencer to check the expiration of
   * @param effectiveTime - the time to compare the sequencer to
   * @return true if the sequencer is expired and false if it is not expired
   */
  public static boolean isExpired(Sequencer sequencer, Instant effectiveTime) {
    return effectiveTime.isAfter(getExpirationInstant(sequencer));
  }

  /**
   * Checks if a request and local sequencer are the same in a shallow way.
   *
   * <p>Note that this does not actually check if the request sequencer is valid</p>
   *
   * @param request - the request sequencer to compare
   * @param local   - the local sequencer to compare
   * @return true if these are referring to the same sequencer
   */
  public static boolean checkSequencer(Sequencer request, Sequencer local) {
    return request.getLockName().equals(local.getLockName())
        && request.getSequenceNumber() == local.getSequenceNumber();
  }

  /**
   * Verify that the request Sequencer is using the same key that is hashed for the local Sequencer.
   * If the two sequencers do not match, then an exception is thrown.
   *
   * @param request         - the request Sequencer to compare
   * @param local           - the local Sequencer to compare
   * @param digestAlgorithm - the digest algorithm used to hash the key for the local Sequencer
   * @throws InvalidSequencerException if the sequencers don't match or the request sequencer key
   *                                   hash doesn't match the local key hash
   */
  public static void verifySequencer(
      Sequencer request,
      Sequencer local,
      String digestAlgorithm
  ) throws InvalidSequencerException {
    final String hashedRequestKey = KeyUtil.hashKey(request.getKey(), digestAlgorithm);
    boolean validSequencer = checkSequencer(request, local)
        && hashedRequestKey.equals(local.getKey())
        && request.getExpiration() == local.getExpiration();

    if (!validSequencer) {
      throw new InvalidSequencerException(local.getLockName());
    }
  }
}
