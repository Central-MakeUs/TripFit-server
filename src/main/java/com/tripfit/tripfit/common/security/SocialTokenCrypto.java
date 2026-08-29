package com.tripfit.tripfit.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SocialTokenCrypto {

  private static final String ALGORITHM = "AES/GCM/NoPadding";

  private static final int IV_LENGTH_BYTES = 12;

  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final SocialTokenCryptoProperties socialTokenCryptoProperties;

  private final Environment environment;

  private final SecureRandom secureRandom = new SecureRandom();

  private volatile SecretKeySpec secretKey;

  public SocialTokenCrypto(
      SocialTokenCryptoProperties socialTokenCryptoProperties, Environment environment) {
    this.socialTokenCryptoProperties = socialTokenCryptoProperties;
    this.environment = environment;
  }

  // 평문 소셜 OAuth refresh token → IV+암호문 Base64
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    SecretKeySpec key = secretKey();
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to encrypt social OAuth token", exception);
    }
  }

  // IV+암호문 Base64 → 평문 소셜 OAuth refresh token
  public String decrypt(String ciphertextBase64) {
    if (ciphertextBase64 == null) {
      return null;
    }
    SecretKeySpec key = secretKey();
    try {
      byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
      byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to decrypt social OAuth token", exception);
    }
  }

  private SecretKeySpec secretKey() {
    if (secretKey != null) {
      return secretKey;
    }
    synchronized (this) {
      if (secretKey != null) {
        return secretKey;
      }
      String encodedKey = socialTokenCryptoProperties.getTokenAesKey();
      if (!isTestProfile() && (encodedKey == null || encodedKey.isBlank())) {
        throw new IllegalStateException(
            "SOCIAL_TOKEN_AES_KEY is required for social OAuth token encryption");
      }
      secretKey = decodeKey(encodedKey);
      return secretKey;
    }
  }

  private boolean isTestProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains("test");
  }

  private static SecretKeySpec decodeKey(String encodedKey) {
    byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
    if (keyBytes.length != 32) {
      throw new IllegalStateException("SOCIAL_TOKEN_AES_KEY must decode to 32 bytes for AES-256");
    }
    return new SecretKeySpec(keyBytes, "AES");
  }
}
