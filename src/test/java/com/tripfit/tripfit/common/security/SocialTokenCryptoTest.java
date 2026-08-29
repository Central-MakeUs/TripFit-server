package com.tripfit.tripfit.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class SocialTokenCryptoTest {

  private static final String VALID_32_BYTE_KEY =
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  @Mock
  private Environment environment;

  @Test
  void encryptThenDecrypt_roundTripsToOriginalPlaintext() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    SocialTokenCryptoProperties properties = properties(VALID_32_BYTE_KEY);
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    String ciphertext = crypto.encrypt("social-refresh-token");

    assertThat(ciphertext).isNotEqualTo("social-refresh-token");
    assertThat(crypto.decrypt(ciphertext)).isEqualTo("social-refresh-token");
  }

  @Test
  void encrypt_whenPlaintextNull_returnsNull() {
    SocialTokenCryptoProperties properties = properties(VALID_32_BYTE_KEY);
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    assertThat(crypto.encrypt(null)).isNull();
  }

  @Test
  void decrypt_whenCiphertextNull_returnsNull() {
    SocialTokenCryptoProperties properties = properties(VALID_32_BYTE_KEY);
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    assertThat(crypto.decrypt(null)).isNull();
  }

  @Test
  void encrypt_twice_producesDifferentCiphertextForSamePlaintext() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    SocialTokenCryptoProperties properties = properties(VALID_32_BYTE_KEY);
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    String first = crypto.encrypt("same-plaintext");
    String second = crypto.encrypt("same-plaintext");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void encrypt_whenKeyBlankAndNotTestProfile_throwsIllegalState() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    SocialTokenCryptoProperties properties = properties("");
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    assertThatThrownBy(() -> crypto.encrypt("plaintext"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SOCIAL_TOKEN_AES_KEY is required");
  }

  @Test
  void encrypt_whenKeyDoesNotDecodeTo32Bytes_throwsIllegalState() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    SocialTokenCryptoProperties properties = properties("dG9vc2hvcnQ=");
    SocialTokenCrypto crypto = new SocialTokenCrypto(properties, environment);

    assertThatThrownBy(() -> crypto.encrypt("plaintext"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  private static SocialTokenCryptoProperties properties(String tokenAesKey) {
    SocialTokenCryptoProperties properties = new SocialTokenCryptoProperties();
    properties.setTokenAesKey(tokenAesKey);
    return properties;
  }
}
