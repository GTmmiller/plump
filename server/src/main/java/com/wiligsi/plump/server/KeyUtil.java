package com.wiligsi.plump.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

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
   * Create a unique hash of a Base64 encoded key.
   *
   * @param key    the key to hash
   * @param digest the hashing algorithm to use on the key
   * @return a unique hash of the passed in key using the passed in algorithm
   */
  static String hashKey(String key, MessageDigest digest) {
    final byte[] hashedBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
    final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(hashedBytes);
  }
}
