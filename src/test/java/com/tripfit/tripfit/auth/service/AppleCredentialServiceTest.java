package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.oauth.AppleOAuthClient;
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

// credential 생성/갱신/삭제 세부 동작(신규 vs 기존 overwrite)은 AppleCredentialPersistenceServiceTest 담당 —
// 여기서는 saveIfAuthorizationCodePresent/revokeAndDeleteIfPresent가 HTTP 교환·revoke와 persistenceService
// 위임을 best-effort로 올바르게 조율하는지만 검증
@ExtendWith(MockitoExtension.class)
class AppleCredentialServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private AppleOAuthClient appleOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  @Mock
  private AppleCredentialPersistenceService persistenceService;

  @InjectMocks
  private AppleCredentialService appleCredentialService;

  @Test
  void saveIfAuthorizationCodePresent_whenCodeBlank_doesNothing() {
    User user = user();

    appleCredentialService.saveIfAuthorizationCodePresent(user, "  ", "com.tripfit.app");

    verify(appleOAuthClient, never()).exchangeAuthorizationCodeForRefreshToken(any(), any());
    verify(persistenceService, never()).save(any(), any(), any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExchangeSucceeds_savesEncryptedCredential() {
    User user = user();
    when(appleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code", "com.tripfit.app"))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");

    appleCredentialService.saveIfAuthorizationCodePresent(user, "auth-code", "com.tripfit.app");

    verify(persistenceService).save(user, "encrypted-refresh", "com.tripfit.app");
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

    verify(persistenceService, never()).save(any(), any(), any());
  }

  @Test
  void revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesWithStoredClientIdThenDeletes() {
    User user = user();
    AppleCredential credential = AppleCredential.create(user, "ciphertext", "com.tripfit.service");
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("ciphertext")).thenReturn("plain-refresh");

    appleCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(appleOAuthClient).revokeRefreshToken("plain-refresh", "com.tripfit.service");
    verify(persistenceService).deleteByUserId(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenNoCredential_stillCallsDelete() {
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.empty());

    appleCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(appleOAuthClient, never()).revokeRefreshToken(any(), any());
    verify(persistenceService).deleteByUserId(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes() {
    User user = user();
    AppleCredential credential =
        AppleCredential.create(user, "corrupt-ciphertext", "com.tripfit.app");
    when(persistenceService.findByUserId(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("corrupt-ciphertext"))
        .thenThrow(new IllegalStateException("decrypt failed"));

    assertThatCode(() -> appleCredentialService.revokeAndDeleteIfPresent(USER_ID))
        .doesNotThrowAnyException();

    verify(persistenceService).deleteByUserId(USER_ID);
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
