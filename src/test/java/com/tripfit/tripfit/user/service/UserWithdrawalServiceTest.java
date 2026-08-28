package com.tripfit.tripfit.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private GoogleCalendarService googleCalendarService;

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
            googleCalendarService,
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
  void withdraw_delegatesGoogleCalendarRevokeBeforeFinalizing() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarService).revokeIfConnected(USER_ID);
    verify(persistenceService).finalizeWithdrawal(USER_ID);
  }

  @Test
  void withdraw_whenGoogleCalendarRevokeThrows_stillFinalizesWithdrawal() {
    User user = user();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    doThrow(new RuntimeException("decrypt failed"))
        .when(googleCalendarService)
        .revokeIfConnected(USER_ID);

    userWithdrawalService.withdraw(USER_ID);

    verify(persistenceService).finalizeWithdrawal(USER_ID);
  }

  @Test
  void withdraw_whenKakaoProvider_callsKakaoUnlink() {
    User user = kakaoUser();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(kakaoUnlinkClient).unlink("kakao-sub");
  }

  @Test
  void withdraw_whenKakaoProviderWithGoogleCalendarConnected_revokesCalendarAndUnlinksKakao() {
    User user = kakaoUser();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarService).revokeIfConnected(USER_ID);
    verify(kakaoUnlinkClient).unlink("kakao-sub");
  }

  @Test
  void withdraw_whenAppleProviderWithGoogleCalendarConnected_revokesCalendarAndAppleCredential() {
    User user = appleUser();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    userWithdrawalService.withdraw(USER_ID);

    verify(googleCalendarService).revokeIfConnected(USER_ID);
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
