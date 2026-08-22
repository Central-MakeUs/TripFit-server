package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleLoginCredentialServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  @Mock
  private GoogleLoginCredentialRepository googleLoginCredentialRepository;

  @Mock
  private GoogleOAuthClient googleOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  private GoogleLoginCredentialService googleLoginCredentialService;

  @BeforeEach
  void setUp() {
    googleLoginCredentialService =
        new GoogleLoginCredentialService(
            googleLoginCredentialRepository,
            googleOAuthClient,
            tokenCrypto);
  }

  @Test
  void saveIfAuthorizationCodePresent_whenCodeBlank_doesNothing() {
    User user = user();

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "  ");

    verify(googleOAuthClient, never()).exchangeAuthorizationCodeForRefreshToken(any());
    verify(googleLoginCredentialRepository, never()).save(any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenNewUserAndRefreshTokenPresent_createsCredential() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code"))
        .thenReturn("plain-refresh");
    when(tokenCrypto.encrypt("plain-refresh")).thenReturn("encrypted-refresh");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "auth-code");

    verify(googleLoginCredentialRepository)
        .save(
            ArgumentMatchers.argThat(
                credential -> "encrypted-refresh".equals(credential.getRefreshTokenCiphertext())));
  }

  // Google은 재로그인마다 refresh_token을 내려주지 않으므로(최초 동의 시에만), null 응답은 정상 케이스로 skip해야 함
  @Test
  void saveIfAuthorizationCodePresent_whenRefreshTokenAbsent_skipsSaveAndKeepsExisting() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("auth-code")).thenReturn(null);

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "auth-code");

    verify(googleLoginCredentialRepository, never()).findByUser_Id(any());
    verify(googleLoginCredentialRepository, never()).save(any());
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExistingCredential_overwritesRefreshToken() {
    User user = user();
    GoogleLoginCredential existing = GoogleLoginCredential.create(user, "old-ciphertext");
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("new-code"))
        .thenReturn("new-plain-refresh");
    when(tokenCrypto.encrypt("new-plain-refresh")).thenReturn("new-ciphertext");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

    googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "new-code");

    verify(googleLoginCredentialRepository).save(existing);
    org.assertj.core.api.Assertions.assertThat(existing.getRefreshTokenCiphertext())
        .isEqualTo("new-ciphertext");
  }

  @Test
  void saveIfAuthorizationCodePresent_whenExchangeFails_doesNotThrowAndSkipsSave() {
    User user = user();
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken("bad-code"))
        .thenThrow(new IllegalStateException("Google token endpoint error"));

    assertThatCode(
        () -> googleLoginCredentialService.saveIfAuthorizationCodePresent(user, "bad-code"))
        .doesNotThrowAnyException();

    verify(googleLoginCredentialRepository, never()).save(any());
  }

  @Test
  void revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesThenDeletes() {
    User user = user();
    GoogleLoginCredential credential = GoogleLoginCredential.create(user, "ciphertext");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("ciphertext")).thenReturn("plain-refresh");

    googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(googleOAuthClient).revokeRefreshToken("plain-refresh");
    verify(googleLoginCredentialRepository).deleteByUser_Id(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenNoCredential_stillCallsDelete() {
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID);

    verify(googleOAuthClient, never()).revokeRefreshToken(any());
    verify(googleLoginCredentialRepository).deleteByUser_Id(USER_ID);
  }

  @Test
  void revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes() {
    User user = user();
    GoogleLoginCredential credential = GoogleLoginCredential.create(user, "corrupt-ciphertext");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("corrupt-ciphertext"))
        .thenThrow(new IllegalStateException("decrypt failed"));

    assertThatCode(() -> googleLoginCredentialService.revokeAndDeleteIfPresent(USER_ID))
        .doesNotThrowAnyException();

    verify(googleLoginCredentialRepository).deleteByUser_Id(USER_ID);
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
