package com.tripfit.tripfit.user.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.service.UserLookupService;
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
class GoogleCalendarSyncPersistenceServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Mock
  private GoogleCalendarCredentialRepository credentialRepository;

  @Mock
  private GoogleCalendarBusyDayRepository busyDayRepository;

  @Mock
  private UserLookupService userLookupService;

  @InjectMocks
  private GoogleCalendarSyncPersistenceService persistenceService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(USER_ID);
  }

  @Test
  void saveConnectedCredential_whenNoExisting_createsAndConnects() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
    when(credentialRepository.save(any(GoogleCalendarCredential.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    persistenceService.saveConnectedCredential(
        USER_ID,
        "enc-refresh",
        "enc-access",
        Instant.now().plusSeconds(3600),
        "a@gmail.com");

    ArgumentCaptor<GoogleCalendarCredential> captor =
        ArgumentCaptor.forClass(GoogleCalendarCredential.class);
    verify(credentialRepository).save(captor.capture());
    assertThat(captor.getValue().getGoogleAccountEmail()).isEqualTo("a@gmail.com");
    assertThat(user.isGoogleCalendarConnected()).isTrue();
  }

  @Test
  void saveConnectedCredential_whenExisting_updatesTokensWithoutCreatingNew() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    GoogleCalendarCredential existing =
        GoogleCalendarCredential.create(
            user,
            "old-refresh",
            "old-access",
            Instant.now(),
            "old@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

    persistenceService.saveConnectedCredential(
        USER_ID,
        "new-refresh",
        "new-access",
        Instant.now().plusSeconds(3600),
        "new@gmail.com");

    assertThat(existing.getRefreshTokenCiphertext()).isEqualTo("new-refresh");
    assertThat(existing.getGoogleAccountEmail()).isEqualTo("new@gmail.com");
    assertThat(user.isGoogleCalendarConnected()).isTrue();
    verify(credentialRepository).save(existing);
  }

  @Test
  void applySyncSuccess_replacesBusyDaysAndMarksSynced() {
    GoogleCalendarCredential credential =
        GoogleCalendarCredential
            .create(user, "enc-refresh", "enc-access", Instant.now(), "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    LocalDate windowStart = LocalDate.now(SEOUL);
    LocalDate windowEnd = windowStart.plusDays(1);
    when(
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            windowStart,
            windowEnd))
        .thenReturn(List.of());
    Instant busyStart = windowStart.atStartOfDay(SEOUL).plusHours(10).toInstant();
    Instant busyEnd = windowStart.atStartOfDay(SEOUL).plusHours(11).toInstant();
    AccessTokenResolution resolution = new AccessTokenResolution("access", null, null, null);

    persistenceService.applySyncSuccess(
        USER_ID,
        resolution,
        windowStart,
        windowEnd,
        List.of(new GoogleFreeBusyInterval(busyStart, busyEnd)));

    verify(busyDayRepository).deleteByUser_IdAndScheduleDateBefore(USER_ID, windowStart);
    verify(busyDayRepository).deleteByUser_IdAndScheduleDateAfter(USER_ID, windowEnd);
    verify(busyDayRepository).save(any(GoogleCalendarBusyDay.class));
    assertThat(credential.getLastSyncedAt()).isNotNull();
  }

  @Test
  void applySyncSuccess_whenAccessTokenRefreshed_updatesCache() {
    GoogleCalendarCredential credential =
        GoogleCalendarCredential
            .create(user, "enc-refresh", "old-access", Instant.now(), "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));
    LocalDate windowStart = LocalDate.now(SEOUL);
    LocalDate windowEnd = windowStart.plusDays(1);
    when(
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            windowStart,
            windowEnd))
        .thenReturn(List.of());
    Instant newExpiry = Instant.now().plusSeconds(3600);
    AccessTokenResolution resolution =
        new AccessTokenResolution("new-access", "new-access-cipher", newExpiry,
            "new-refresh-cipher");

    persistenceService.applySyncSuccess(USER_ID, resolution, windowStart, windowEnd, List.of());

    assertThat(credential.getAccessTokenCiphertext()).isEqualTo("new-access-cipher");
    assertThat(credential.getAccessTokenExpiresAt()).isEqualTo(newExpiry);
    assertThat(credential.getRefreshTokenCiphertext()).isEqualTo("new-refresh-cipher");
  }

  @Test
  void applySyncSuccess_whenCredentialMissing_isNoOp() {
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    persistenceService.applySyncSuccess(
        USER_ID,
        new AccessTokenResolution("access", null, null, null),
        LocalDate.now(),
        LocalDate.now(),
        List.of());

    verify(busyDayRepository, never()).deleteByUser_IdAndScheduleDateBefore(any(), any());
  }

  @Test
  void applySyncError_marksSyncErrorWithoutTouchingFlag() {
    GoogleCalendarCredential credential =
        GoogleCalendarCredential
            .create(user, "enc-refresh", "enc-access", Instant.now(), "a@gmail.com");
    when(credentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));

    persistenceService.applySyncError(USER_ID, "429 TOO_MANY_REQUESTS");

    assertThat(credential.getLastSyncError()).isEqualTo("429 TOO_MANY_REQUESTS");
    verify(credentialRepository, never()).deleteByUser_Id(any());
  }

  @Test
  void disconnectGoogleCalendar_deletesGoogleLayerAndClearsFlag() {
    user.connectGoogleCalendar();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    persistenceService.disconnectGoogleCalendar(USER_ID);

    verify(credentialRepository).deleteByUser_Id(USER_ID);
    verify(busyDayRepository).deleteByUser_Id(USER_ID);
    assertThat(user.isGoogleCalendarConnected()).isFalse();
  }

  @Test
  void clearConnectedFlag_setsFalseWithoutDeleting() {
    user.connectGoogleCalendar();
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    persistenceService.clearConnectedFlag(USER_ID);

    assertThat(user.isGoogleCalendarConnected()).isFalse();
    verify(credentialRepository, never()).deleteByUser_Id(any());
  }
}
