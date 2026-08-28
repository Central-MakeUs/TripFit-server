package com.tripfit.tripfit.user.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class GoogleCalendarSyncPersistenceIntegrationTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Autowired
  private GoogleCalendarSyncPersistenceService persistenceService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private GoogleCalendarCredentialRepository credentialRepository;

  @Autowired
  private GoogleCalendarBusyDayRepository busyDayRepository;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            new User(
                "gcal-persistence-" + java.util.UUID.randomUUID(),
                SocialProvider.GOOGLE,
                "gcal-persistence@example.com",
                "닉네임",
                null));
  }

  @Test
  void saveConnectedCredential_thenReReadFromRepository_isCommittedAndConnectsFlag() {
    persistenceService.saveConnectedCredential(
        user.getId(),
        "enc-refresh",
        "enc-access",
        Instant.now().plusSeconds(3600),
        "gcal@gmail.com");

    Optional<GoogleCalendarCredential> reloaded = credentialRepository.findByUser_Id(user.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getGoogleAccountEmail()).isEqualTo("gcal@gmail.com");

    User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloadedUser.isGoogleCalendarConnected()).isTrue();
  }

  @Test
  void applySyncSuccess_persistsBusyDaysAndMarksSyncedAcrossTransactionBoundary() {
    persistenceService.saveConnectedCredential(
        user.getId(),
        "enc-refresh",
        "enc-access",
        Instant.now().plusSeconds(3600),
        null);

    LocalDate windowStart = LocalDate.now(SEOUL);
    LocalDate windowEnd = windowStart.plusDays(3);
    Instant busyStart = windowStart.atStartOfDay(SEOUL).plusHours(10).toInstant();
    Instant busyEnd = windowStart.atStartOfDay(SEOUL).plusHours(11).toInstant();
    AccessTokenResolution resolution = new AccessTokenResolution("access-token", null, null, null);

    persistenceService.applySyncSuccess(
        user.getId(),
        resolution,
        windowStart,
        windowEnd,
        List.of(new GoogleFreeBusyInterval(busyStart, busyEnd)));

    List<GoogleCalendarBusyDay> days =
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            user.getId(),
            windowStart,
            windowEnd);
    assertThat(days).hasSize(1);

    assertThat(days.getFirst().isMorningBusy()).isTrue();
    assertThat(days.getFirst().isAfternoonBusy()).isFalse();

    GoogleCalendarCredential reloadedCredential =
        credentialRepository.findByUser_Id(user.getId()).orElseThrow();
    assertThat(reloadedCredential.getLastSyncedAt()).isNotNull();
  }

  @Test
  void disconnectGoogleCalendar_deletesGoogleLayerAndClearsFlagInRealDb() {
    persistenceService.saveConnectedCredential(
        user.getId(),
        "enc-refresh",
        "enc-access",
        Instant.now().plusSeconds(3600),
        null);

    persistenceService.disconnectGoogleCalendar(user.getId());

    assertThat(credentialRepository.findByUser_Id(user.getId())).isEmpty();
    assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleCalendarConnected())
        .isFalse();
  }
}
