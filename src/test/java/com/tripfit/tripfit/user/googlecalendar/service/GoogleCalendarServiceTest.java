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

                true));

    googleCalendarService.connect(USER_ID, "auth-code", redirectUri);

    verify(googleCalendarOAuthClient).exchangeAuthorizationCode("auth-code", redirectUri);
  }

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

                true));

    googleCalendarService.connect(USER_ID, "auth-code", null);

    verify(persistenceService).applySyncError(eq(USER_ID), anyString());
    verify(persistenceService, never()).disconnectGoogleCalendar(USER_ID);
  }

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
