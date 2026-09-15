package com.tripfit.tripfit.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleCalendarOAuthClient;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// cascade·hard delete·soft delete(DB 쓰기)는 UserWithdrawalPersistenceService로 위임되므로(A-2), 이
// 테스트는 UserWithdrawalService가 provider revoke 호출들을 올바른 조건으로 실행하고 persistenceService에
// 위임하는지만 검증한다. 실제 DB 반영은 UserWithdrawalPersistenceServiceTest가 검증한다
@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  @Mock
  private GoogleCalendarOAuthClient googleCalendarOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  @Mock
  private KakaoUnlinkClient kakaoUnlinkClient;

  @Mock
  private AppleCredentialService appleCredentialService;

  @Mock
  private GoogleLoginCredentialService googleLoginCredentialService;

  @Mock
  private UserWithdrawalPersistenceService persistenceService;

  private UserWithdrawalService userWithdrawalService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    userWithdrawalService =
        new UserWithdrawalService(
            userLookupService,
            googleCalendarCredentialRepository,
            googleCalendarOAuthClient,
            tokenCrypto,
            kakaoUnlinkClient,
            appleCredentialService,
            googleLoginCredentialService,
            persistenceService);
  }

  @Test
  void withdraw_delegatesFinalizationToPersistenceService() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(persistenceService).finalizeWithdrawal(USER_ID);
  }

  @Test
  void withdraw_whenGoogleCalendarConnected_revokesRefreshTokenBeforeFinalizing() {
    User user = user();
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(user, "encrypted-refresh", "encrypted-access", null, null);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("encrypted-refresh")).thenReturn("plain-refresh");

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarOAuthClient).revokeRefreshToken(USER_ID, "plain-refresh");
    verify(persistenceService).finalizeWithdrawal(USER_ID);
  }

  @Test
  void withdraw_whenGoogleCalendarNotConnected_doesNotRevoke() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarOAuthClient, never()).revokeRefreshToken(any(), any());
  }

  @Test
  void withdraw_whenKakaoProvider_callsKakaoUnlink() {
    User user = kakaoUser();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(kakaoUnlinkClient).unlink("kakao-sub");
  }

  // #78 검증 — Kakao 로그인 유저가 Google Calendar만 연동한 상태로 탈퇴해도 Calendar revoke는 provider와 무관하게 항상 실행됨
  @Test
  void withdraw_whenKakaoProviderWithGoogleCalendarConnected_revokesCalendarAndUnlinksKakao() {
    User user = kakaoUser();
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(user, "encrypted-refresh", "encrypted-access", null, null);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("encrypted-refresh")).thenReturn("plain-refresh");

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarOAuthClient).revokeRefreshToken(USER_ID, "plain-refresh");
    verify(kakaoUnlinkClient).unlink("kakao-sub");
  }

  // #78 검증 — Apple 로그인 유저가 Google Calendar만 연동한 상태로 탈퇴해도 Calendar revoke는 provider와 무관하게 항상 실행됨
  @Test
  void withdraw_whenAppleProviderWithGoogleCalendarConnected_revokesCalendarAndAppleCredential() {
    User user = appleUser();
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(user, "encrypted-refresh", "encrypted-access", null, null);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("encrypted-refresh")).thenReturn("plain-refresh");

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarOAuthClient).revokeRefreshToken(USER_ID, "plain-refresh");
    verify(appleCredentialService).revokeAndDeleteIfPresent(USER_ID);
  }

  @Test
  void withdraw_whenNotKakaoProvider_doesNotCallKakaoUnlink() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(kakaoUnlinkClient, never()).unlink(any());
  }

  @Test
  void withdraw_callsAppleCredentialRevokeAndDelete() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(appleCredentialService).revokeAndDeleteIfPresent(USER_ID);
  }

  @Test
  void withdraw_callsGoogleLoginCredentialRevokeAndDelete() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(googleLoginCredentialService).revokeAndDeleteIfPresent(USER_ID);
  }

  @Test
  void withdraw_whenAlreadyWithdrawn_isIdempotentNoOp() {
    User user = user();
    user.markDeleted();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(appleCredentialService, never()).revokeAndDeleteIfPresent(any());
    verify(googleLoginCredentialService, never()).revokeAndDeleteIfPresent(any());
    verify(persistenceService, never()).finalizeWithdrawal(any());
  }

  private static User user() {
    User user =
        new User(
            "google-sub",
            SocialProvider.GOOGLE,
            "user@example.com",
            "닉네임",
            "https://example.com/profile.png");
    user.setId(USER_ID);
    user.applyProfilePatch("길동", "홍", null);
    user.connectGoogleCalendar();
    return user;
  }

  private static User kakaoUser() {
    User user =
        new User(
            "kakao-sub",
            SocialProvider.KAKAO,
            "user@example.com",
            "닉네임",
            "https://example.com/profile.png");
    user.setId(USER_ID);
    return user;
  }

  private static User appleUser() {
    User user =
        new User(
            "apple-sub",
            SocialProvider.APPLE,
            "user@example.com",
            "닉네임",
            "https://example.com/profile.png");
    user.setId(USER_ID);
    return user;
  }
}
