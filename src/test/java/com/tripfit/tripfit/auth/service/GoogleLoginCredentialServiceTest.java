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
    googleLoginCredentialService.saveIfAuthorizationCodePresent(USER_ID, "  ", null);

    verify(googleOAuthClient, never()).exchangeAuthorizationCodeForRefreshToken(any(), any());
    verify(persistenceService, never()).saveForActiveUser(any(), any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRefreshTokenPresent_savesEncryptedCredential() {
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", null))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");
    when(persistenceService.saveForActiveUser(USER_ID, "encrypted-refresh")).thenReturn(true);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(USER_ID, "auth-code", null);

    verify(persistenceService).saveForActiveUser(USER_ID, "encrypted-refresh");
    verify(googleOAuthClient, never()).revokeRefreshToken(any());
  }

  // 교환이 끝나기 전에 탈퇴한 사용자에게는 저장하지 않고, 받은 토큰으로 Google 연결을 바로 끊는다.
  @Test
  void saveIfAuthorizationCodePresent_whenUserWithdrewDuringExchange_revokesIssuedToken() {
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", null))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");
    when(persistenceService.saveForActiveUser(USER_ID, "encrypted-refresh")).thenReturn(false);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(USER_ID, "auth-code", null);

    verify(googleOAuthClient).revokeRefreshToken("plain-refresh");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRedirectUriPresent_passesItToClient() {
    when(
        googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(
            "auth-code",
            "https://tripfit.online/auth/google/callback"))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");
    when(persistenceService.saveForActiveUser(USER_ID, "encrypted-refresh")).thenReturn(true);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(
        USER_ID,
        "auth-code",
        "https://tripfit.online/auth/google/callback");

    verify(googleOAuthClient)
        .exchangeAuthorizationCodeForRefreshToken(
            "auth-code",
            "https://tripfit.online/auth/google/callback");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenRefreshTokenAbsent_skipsSave() {
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", null))
        .thenReturn(null);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(USER_ID, "auth-code", null);

    verify(persistenceService, never()).saveForActiveUser(any(), any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExchangeFails_doesNotThrowAndSkipsSave() {
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("bad-code", null))
        .thenThrow(new IllegalStateException("Google token endpoint error"));

    assertThatCode(
        () -> googleLoginCredentialService
            .saveIfAuthorizationCodePresent(USER_ID, "bad-code", null))
        .doesNotThrowAnyException();

    verify(persistenceService, never()).saveForActiveUser(any(), any());
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
