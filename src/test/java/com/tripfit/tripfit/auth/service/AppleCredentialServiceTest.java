package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.oauth.AppleOAuthClient;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleCredentialServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private AppleCredentialRepository appleCredentialRepository;

  @Mock
  private AppleOAuthClient appleOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  private AppleCredentialService appleCredentialService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    appleCredentialService =
        new AppleCredentialService(appleCredentialRepository, appleOAuthClient, tokenCrypto);
  }

  @Test
  void saveIfAuthorizationCodePresent_whenCodeBlank_doesNothing() {
    User user = user();

    appleCredentialService.saveIfAuthorizationCodePresent(user, "  ", "com.tripfit.app");

    verify(appleOAuthClient, never()).exchangeAuthorizationCodeForRefreshToken(any(), any());
    verify(appleCredentialRepository, never()).save(any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenNewUser_createsCredential() {
    User user = user();
    when(appleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", "com.tripfit.app"))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    appleCredentialService.saveIfAuthorizationCodePresent(user, "auth-code", "com.tripfit.app");

    verify(appleCredentialRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                credential -> "encrypted-refresh".equals(credential.getRefreshTokenCiphertext())
                    && "com.tripfit.app".equals(credential.getAppleClientId())));
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExistingCredential_overwritesRefreshTokenAndClientId() {
    User user = user();
    AppleCredential existing = AppleCredential.create(user, "old-ciphertext", "com.tripfit.app");
    when(
        appleOAuthClient.exchangeAuthorizationCodeForRefreshToken(
            "new-code",
            "com.tripfit.service"))
        .thenReturn("new-plain-refresh");
    when(tokenCrypto.encrypt("new-plain-refresh")).thenReturn("new-ciphertext");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

    appleCredentialService.saveIfAuthorizationCodePresent(
        user,
        "new-code",
        "com.tripfit.service");

    verify(appleCredentialRepository).save(existing);
    org.assertj.core.api.Assertions.assertThat(existing.getRefreshTokenCiphertext())
        .isEqualTo("new-ciphertext");
    org.assertj.core.api.Assertions.assertThat(existing.getAppleClientId())
        .isEqualTo("com.tripfit.service");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExchangeFails_doesNotThrowAndSkipsSave() {
    User user = user();
    when(appleOAuthClient.exchangeAuthorizationCodeForRefreshToken("bad-code", "com.tripfit.app"))
        .thenThrow(new IllegalStateException("Apple token endpoint error"));

    assertThatCode(
        () -> appleCredentialService.saveIfAuthorizationCodePresent(
            user,
            "bad-code",
            "com.tripfit.app"))
        .doesNotThrowAnyException();

    verify(appleCredentialRepository, never()).save(any());
  }

  @Test
  void revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesWithStoredClientIdThenDeletes() {
    User user = user();
    AppleCredential credential = AppleCredential.create(user, "ciphertext", "com.tripfit.service");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("ciphertext")).thenReturn("plain-refresh");

    appleCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(appleOAuthClient).revokeRefreshToken("plain-refresh", "com.tripfit.service");
    verify(appleCredentialRepository).deleteByUser_Id(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenNoCredential_stillCallsDelete() {
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    appleCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(appleOAuthClient, never()).revokeRefreshToken(any(), any());
    verify(appleCredentialRepository).deleteByUser_Id(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes() {
    User user = user();
    AppleCredential credential =
        AppleCredential.create(user, "corrupt-ciphertext", "com.tripfit.app");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("corrupt-ciphertext"))
        .thenThrow(new IllegalStateException("decrypt failed"));

    assertThatCode(() -> appleCredentialService.revokeAndDeleteIfPresent(USER_ID))
        .doesNotThrowAnyException();

    verify(appleCredentialRepository).deleteByUser_Id(USER_ID);
  }

  private static User user() {
    User user =
        new User(
            "apple-sub",
            SocialProvider.APPLE,
            "user@example.com",
            "닉네임",
            null);
    user.setId(USER_ID);
    return user;
  }
}
