package com.wiligsi.plump.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * A class containing static methods for generating and hashing keys.
 *
 * <p>Secure random generation is used to make sure the random keys
 * are difficult to crack
 * </p>
 *
 * @author Steven Miller
 */
public class KeyUtil {

  private static final Logger LOG = Logger.getLogger(KeyUtil.class.getName());

  private KeyUtil() {

  }

  /**
   * Generates a random key that's 24 bytes long.
   *
   * @param secureRandom a cryptographically strong random number generator
   * @return a Base64 String representation of the random key
   */
  static String generateRandomKey(SecureRandom secureRandom) {
    byte[] keyBytes = new byte[24];
    secureRandom.nextBytes(keyBytes);
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(keyBytes);
  }

  /**
   * Create a unique hash of a Base64 encoded key. If the digestAlgorithm cannot be used then a
   * runtime exception is thrown due to security reasons.
   *
   * @param key             - the key to hash
   * @param digestAlgorithm - the hashing algorithm to use on the key
   * @return a unique hash of the passed in key using the passed in algorithm
   */
  static String hashKey(String key, String digestAlgorithm) {
    try {
      final MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
      final byte[] hashedBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
      final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      return encoder.encodeToString(hashedBytes);
    } catch (NoSuchAlgorithmException exception) {
      LOG.severe("Passed in digest algorithm '" + digestAlgorithm + "' could not be found.");
      throw new RuntimeException(exception);
    }
  }
}
