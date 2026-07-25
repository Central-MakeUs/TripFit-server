package com.tripfit.tripfit.user.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private GoogleCalendarOAuthClient googleCalendarOAuthClient;

  @Mock
  private GoogleCalendarTokenCrypto tokenCrypto;

  @Mock
  private GoogleCalendarCredentialRepository credentialRepository;

  @Mock
  private GoogleCalendarBusyDayRepository busyDayRepository;

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private UserSummaryService userSummaryService;

  @InjectMocks
  private GoogleCalendarService googleCalendarService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(USER_ID);
  }

  @Test
  void connect_setsFlagAndSyncs() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(googleCalendarOAuthClient.exchangeAuthorizationCode("auth-code"))
        .thenReturn(
            new GoogleOAuthTokenResponse(
                "access", "refresh", Instant.now().plusSeconds(3600)));
    when(tokenCrypto.encrypt("refresh")).thenReturn("enc-refresh");
    when(tokenCrypto.encrypt("access")).thenReturn("enc-access");
    when(googleCalendarOAuthClient.fetchGoogleAccountEmail("access"))
        .thenReturn("calendar@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
    when(credentialRepository.save(any(GoogleCalendarCredential.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tokenCrypto.decrypt("enc-access")).thenReturn("access");
    when(googleCalendarOAuthClient.queryFreeBusy(any(), any(), any())).thenReturn(List.of());
    when(
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            eq(USER_ID),
            any(),
            any()))
        .thenReturn(List.of());
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
                false));

    googleCalendarService.connect(USER_ID, "auth-code");

    assertThat(user.isGoogleCalendarConnected()).isTrue();
    verify(credentialRepository, atLeastOnce()).save(any(GoogleCalendarCredential.class));
    verify(googleCalendarOAuthClient).fetchGoogleAccountEmail("access");
    org.mockito.ArgumentCaptor<GoogleCalendarCredential> captor =
        org.mockito.ArgumentCaptor.forClass(GoogleCalendarCredential.class);
    verify(credentialRepository, atLeastOnce()).save(captor.capture());
    assertThat(captor.getAllValues().getFirst().getGoogleAccountEmail())
        .isEqualTo("calendar@gmail.com");
  }

  @Test
  void syncUser_onAuthFailure_clearsFlagAndGoogleLayer() {
    user.setGoogleCalendarConnected(true);
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

    assertThat(user.isGoogleCalendarConnected()).isFalse();
    verify(credentialRepository).deleteByUser_Id(USER_ID);
    verify(busyDayRepository).deleteByUser_Id(USER_ID);
  }

  @Test
  void disconnect_keepsPersonalSchedules() {
    user.setGoogleCalendarConnected(true);
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
                false));

    googleCalendarService.disconnect(USER_ID);

    assertThat(user.isGoogleCalendarConnected()).isFalse();
    verify(credentialRepository).deleteByUser_Id(USER_ID);
    verify(busyDayRepository).deleteByUser_Id(USER_ID);
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
