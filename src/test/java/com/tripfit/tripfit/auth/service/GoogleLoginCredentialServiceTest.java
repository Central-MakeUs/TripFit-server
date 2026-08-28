package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleLoginCredentialServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  @Mock
  private GoogleOAuthClient googleOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  @Mock
  private GoogleLoginCredentialPersistenceService persistenceService;

  @InjectMocks
  private GoogleLoginCredentialService googleLoginCredentialService;

  @Test
  void saveIfAuthorizationCodePresent_whenCodeBlank_doesNothing() {
    User user = user();

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "  ", null);

    verify(googleOAuthClient, never()).exchangeAuthorizationCodeForRefreshToken(any(), any());
    verify(persistenceService, never()).save(any(), any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRefreshTokenPresent_savesEncryptedCredential() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", null))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "auth-code", null);

    verify(persistenceService).save(user, "encrypted-refresh");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRedirectUriPresent_passesItToClient() {
    User user = user();
    when(
        googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(
            "auth-code",
            "https://tripfit.online/auth/google/callback"))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");

    googleLoginCredentialService.saveIfAuthorizationCodePresent(
        user,
        "auth-code",
        "https://tripfit.online/auth/google/callback");

    verify(googleOAuthClient)
        .exchangeAuthorizationCodeForRefreshToken(
            "auth-code",
            "https://tripfit.online/auth/google/callback");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRefreshTokenAbsent_skipsSave() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", null))
        .thenReturn(null);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "auth-code", null);

    verify(persistenceService, never()).save(any(), any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExchangeFails_doesNotThrowAndSkipsSave() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("bad-code", null))
        .thenThrow(new IllegalStateException("Google token endpoint error"));

    assertThatCode(
        () -> googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "bad-code", null))
        .doesNotThrowAnyException();

    verify(persistenceService, never()).save(any(), any());
  }

  @Test
  void revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesThenDeletes() {
    User user = user();
    GoogleLoginCredential credential = GoogleLoginCredential.create(user, "ciphertext");
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("ciphertext")).thenReturn("plain-refresh");

    googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(googleOAuthClient).revokeRefreshToken("plain-refresh");
    verify(persistenceService).deleteByUserId(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenNoCredential_stillCallsDelete() {
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.empty());

    googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(googleOAuthClient, never()).revokeRefreshToken(any());
    verify(persistenceService).deleteByUserId(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes() {
    User user = user();
    GoogleLoginCredential credential = GoogleLoginCredential.create(user, "corrupt-ciphertext");
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("corrupt-ciphertext"))
        .thenThrow(new IllegalStateException("decrypt failed"));

    assertThatCode(() -> googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID))
        .doesNotThrowAnyException();

    verify(persistenceService).deleteByUserId(USER_ID);
  }

  private static User user() {
    User user =
        new User(
            "google-sub",
            SocialProvider.GOOGLE,
            "user@example.com",
            "닉네임",
            null);
    user.setId(USER_ID);
    return user;
  }
}
