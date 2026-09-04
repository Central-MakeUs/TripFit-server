package com.tripfit.tripfit.user.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleCalendarOAuthClient;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleOAuthTokenResponse;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// DB 쓰기(credential 저장·busy_day 갱신)는 GoogleCalendarSyncPersistenceService로 위임되므로(A-1), 이
// 테스트는 GoogleCalendarService가 Google 서버와의 통신 결과를 올바른 인자로 persistenceService에 넘기는지만
// 검증한다. 실제 DB 반영 로직은 GoogleCalendarSyncPersistenceServiceTest가 검증한다
@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private GoogleCalendarOAuthClient googleCalendarOAuthClient;

  @Mock
  private SocialTokenCrypto tokenCrypto;

  @Mock
  private GoogleCalendarCredentialRepository credentialRepository;

  @Mock
  private GoogleCalendarBusyDayRepository busyDayRepository;

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private UserSummaryService userSummaryService;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private GoogleCalendarSyncPersistenceService persistenceService;

  @InjectMocks
  private GoogleCalendarService googleCalendarService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(USER_ID);
  }

  // resolveAccessToken이 "캐시 유효" 분기를 타도록 만료 전 credential을 돌려줌 — 이후 syncUserInternal이
  // queryFreeBusy까지 정상 진행되는지 확인하기 위함
  private GoogleCalendarCredential validCachedCredential() {
    return GoogleCalendarCredential.create(
        user,
        "enc-refresh",
        "enc-access",
        Instant.now().plusSeconds(3600),
        "a@gmail.com");
  }

  @Test
  void connect_setsFlagAndSyncs() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarOAuthClient.exchangeAuthorizationCode("auth-code", null))
        .thenReturn(
            new GoogleOAuthTokenResponse(
                "access", "refresh", Instant.now().plusSeconds(3600), null));
    when(tokenCrypto.encrypt("refresh")).thenReturn("enc-refresh");
    when(tokenCrypto.encrypt("access")).thenReturn("enc-access");
    when(googleCalendarOAuthClient.fetchGoogleAccountEmail("access"))
        .thenReturn("calendar@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(validCachedCredential()));
    when(tokenCrypto.decrypt("enc-access")).thenReturn("access");
    when(googleCalendarOAuthClient.queryFreeBusy(any(), any(), any(), any())).thenReturn(List.of());
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                USER_ID,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                true,
                false,
                false,
                true));

    googleCalendarService.connect(USER_ID, "auth-code", null);

    verify(persistenceService)
        .saveConnectedCredential(
            eq(USER_ID),
            eq("enc-refresh"),
            eq("enc-access"),
            any(),
            eq("calendar@gmail.com"));
    verify(googleCalendarOAuthClient).fetchGoogleAccountEmail("access");
    verify(persistenceService)
        .applySyncSuccess(eq(USER_ID), any(), any(), any(), eq(List.of()));
  }

  // 브라우저 리다이렉트 경로 — Controller가 받은 redirectUri를 그대로 OAuthClient까지 전달하는지 검증
  @Test
  void connect_whenRedirectUriPresent_forwardsToOAuthClient() {
    String redirectUri = "https://tripfit.online/settings/google-calendar/callback";
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarOAuthClient.exchangeAuthorizationCode("auth-code", redirectUri))
        .thenReturn(
            new GoogleOAuthTokenResponse(
                "access", "refresh", Instant.now().plusSeconds(3600), null));
    when(tokenCrypto.encrypt("refresh")).thenReturn("enc-refresh");
    when(tokenCrypto.encrypt("access")).thenReturn("enc-access");
    when(googleCalendarOAuthClient.fetchGoogleAccountEmail("access")).thenReturn(null);
    when(credentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(validCachedCredential()));
    when(tokenCrypto.decrypt("enc-access")).thenReturn("access");
    when(googleCalendarOAuthClient.queryFreeBusy(any(), any(), any(), any())).thenReturn(List.of());
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                USER_ID,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                true,
                false,
                false,
                true));

    googleCalendarService.connect(USER_ID, "auth-code", redirectUri);

    verify(googleCalendarOAuthClient).exchangeAuthorizationCode("auth-code", redirectUri);
  }

  // 연동 직후 1회 sync가 일시적 오류(429·5xx 등, GoogleCalendarAuthException이 아닌 일반 예외)로 실패해도
  // persistenceService.disconnectGoogleCalendar는 호출되지 않고 applySyncError만 호출되는지 검증 —
  // "연동 성공 직후 DELETE가 연동되어 있지 않음으로 실패"하던 회귀 재현 테스트
  @Test
  void connect_whenInitialSyncFailsWithTransientError_doesNotDisconnect() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarOAuthClient.exchangeAuthorizationCode("auth-code", null))
        .thenReturn(
            new GoogleOAuthTokenResponse(
                "access", "refresh", Instant.now().plusSeconds(3600), null));
    when(tokenCrypto.encrypt("refresh")).thenReturn("enc-refresh");
    when(tokenCrypto.encrypt("access")).thenReturn("enc-access");
    when(googleCalendarOAuthClient.fetchGoogleAccountEmail("access"))
        .thenReturn("calendar@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(validCachedCredential()));
    when(tokenCrypto.decrypt("enc-access")).thenReturn("access");
    when(googleCalendarOAuthClient.queryFreeBusy(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("freeBusy failed: 429 TOO_MANY_REQUESTS"));
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                USER_ID,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                true,
                false,
                false,
                true));

    googleCalendarService.connect(USER_ID, "auth-code", null);

    verify(persistenceService).applySyncError(eq(USER_ID), anyString());
    verify(persistenceService, never()).disconnectGoogleCalendar(USER_ID);
  }

  // code 교환 실패(잘못된 redirect_uri·invalid_grant 등) 시 원인을 로그로 남기고 502로 변환하는지 검증
  @Test
  void connect_whenExchangeFails_throwsConnectFailed() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarOAuthClient.exchangeAuthorizationCode("auth-code", null))
        .thenThrow(new GoogleCalendarAuthException("token endpoint error: 400 BAD_REQUEST"));

    assertThatThrownBy(() -> googleCalendarService.connect(USER_ID, "auth-code", null))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);

    verify(persistenceService, never()).saveConnectedCredential(any(), any(), any(), any(), any());
  }

  @Test
  void syncUser_onAuthFailure_delegatesPermanentAuthFailure() {
    user.connectGoogleCalendar();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(
            user,
            "enc-refresh",
            "enc-access",
            Instant.now().minusSeconds(1),
            "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("enc-refresh")).thenReturn("refresh");
    when(googleCalendarOAuthClient.refreshAccessToken("refresh"))
        .thenThrow(new GoogleCalendarAuthException("invalid_grant"));

    googleCalendarService.syncUser(USER_ID);

    verify(persistenceService).disconnectGoogleCalendar(USER_ID);
    verify(persistenceService, never())
        .applySyncSuccess(any(), any(), any(), any(), any());
  }

  // 진행 중인 여행방의 희망 기간이 C1 기본 윈도우(오늘+2년)보다 길면 sync 윈도우도 그만큼 늘어나는지 검증 —
  // 실제 DB 반영은 persistenceService가 담당하므로, 여기서는 applySyncSuccess에 넘어가는 windowEnd 인자로 확인
  @Test
  void syncUser_whenOngoingTripEndRangeBeyondWindow_extendsSyncWindowEnd() {
    user.connectGoogleCalendar();
    LocalDate extendedEnd = LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2).plusDays(30);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(
            user,
            "enc-refresh",
            "enc-access",
            Instant.now().plusSeconds(3600),
            "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(extendedEnd);
    when(tokenCrypto.decrypt("enc-access")).thenReturn("access");
    when(googleCalendarOAuthClient.queryFreeBusy(any(), any(), any(), any())).thenReturn(List.of());

    googleCalendarService.syncUser(USER_ID);

    ArgumentCaptor<LocalDate> windowEndCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(persistenceService)
        .applySyncSuccess(eq(USER_ID), any(), any(), windowEndCaptor.capture(), eq(List.of()));
    assertThat(windowEndCaptor.getValue()).isEqualTo(extendedEnd);
  }

  @Test
  void syncUser_whenCredentialMissing_clearsConnectedFlag() {
    user.connectGoogleCalendar();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    googleCalendarService.syncUser(USER_ID);

    verify(persistenceService).clearConnectedFlag(USER_ID);
    verify(googleCalendarOAuthClient, never()).queryFreeBusy(any(), any(), any(), any());
  }

  @Test
  void disconnect_keepsPersonalSchedules() {
    user.connectGoogleCalendar();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    GoogleCalendarCredential credential =
        GoogleCalendarCredential.create(user, "enc-refresh", null, null, "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    when(tokenCrypto.decrypt("enc-refresh")).thenReturn("refresh");
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                USER_ID,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                false,
                true,
                false,
                true));

    googleCalendarService.disconnect(USER_ID);

    verify(googleCalendarOAuthClient).revokeRefreshToken(USER_ID, "refresh");
    verify(persistenceService).disconnectGoogleCalendar(USER_ID);
  }

  @Test
  void disconnect_whenNotConnected_throws409() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    assertThatThrownBy(() -> googleCalendarService.disconnect(USER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(GoogleCalendarErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED);
  }
}
