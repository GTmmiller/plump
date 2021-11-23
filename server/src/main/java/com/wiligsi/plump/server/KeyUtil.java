package com.wiligsi.plump.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class KeyUtil {
  private KeyUtil() {

  }

  static String generateRandomKey(SecureRandom secureRandom) {
    byte[] keyBytes = new byte[24];
    secureRandom.nextBytes(keyBytes);
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(keyBytes);
  }

  static String hashKey(String key, MessageDigest digest) {
    final byte[] hashedBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
    final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(hashedBytes);
  }
}
