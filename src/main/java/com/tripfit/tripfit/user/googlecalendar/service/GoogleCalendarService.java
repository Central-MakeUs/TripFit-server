package com.tripfit.tripfit.user.googlecalendar.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.PiiMasker;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleCalendarOAuthClient;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleOAuthTokenResponse;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final String TRIGGER_MANUAL_CONNECT = "MANUAL_CONNECT";

  private static final String TRIGGER_SCHEDULED = "SCHEDULED";

  private final GoogleCalendarOAuthClient googleCalendarOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final GoogleCalendarCredentialRepository credentialRepository;

  private final GoogleCalendarBusyDayRepository busyDayRepository;

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  private final TripMemberRepository tripMemberRepository;

  private final GoogleCalendarSyncPersistenceService persistenceService;

  public UserSummaryResponse connect(UUID userId, String authorizationCode, String redirectUri) {
    userLookupService.requireUser(userId);
    GoogleOAuthTokenResponse tokens;
    boolean hasRedirectUri = redirectUri != null && !redirectUri.isBlank();
    try {
      tokens = googleCalendarOAuthClient.exchangeAuthorizationCode(authorizationCode, redirectUri);
    } catch (GoogleCalendarAuthException exception) {

      SocialIntegrationLog.warn(
          log,
          connectContext(userId),
          "Google Calendar connect failed. authorization code exchange error (hasRedirectUri="
              + hasRedirectUri
              + ")",
          exception);
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);
    } catch (TripFitException exception) {

      SocialIntegrationLog.warn(
          log,
          connectContext(userId),
          "Google Calendar connect failed. token response missing refresh_token (hasRedirectUri="
              + hasRedirectUri
              + ")");
      throw exception;
    }
    if (tokens.scope() != null) {

      SocialIntegrationLog.info(
          log,
          connectContext(userId).withGrantedScope(tokens.scope()),
          "Google Calendar connect token exchange succeeded");
    }

    String refreshCiphertext = tokenCrypto.encrypt(tokens.refreshToken());
    String accessCiphertext = tokenCrypto.encrypt(tokens.accessToken());
    String googleAccountEmail =
        googleCalendarOAuthClient.fetchGoogleAccountEmail(tokens.accessToken());

    persistenceService.saveConnectedCredential(
        userId,
        refreshCiphertext,
        accessCiphertext,
        tokens.accessTokenExpiresAt(),
        googleAccountEmail);
    syncUserInternal(userId, TRIGGER_MANUAL_CONNECT);

    User user = userLookupService.requireUser(userId);
    return userSummaryService.toSummary(user);
  }

  public UserSummaryResponse disconnect(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (!user.isGoogleCalendarConnected()) {
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED);
    }
    revokeIfConnected(userId);
    persistenceService.disconnectGoogleCalendar(userId);
    User updated = userLookupService.requireUser(userId);
    return userSummaryService.toSummary(updated);
  }

  public void revokeIfConnected(UUID userId) {
    credentialRepository
        .findByUser_Id(userId)
        .ifPresent(
            credential -> {
              String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
              googleCalendarOAuthClient.revokeRefreshToken(userId, refreshToken);
            });
  }

  public void syncUser(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (!user.isGoogleCalendarConnected()) {
      return;
    }
    if (credentialRepository.findByUser_Id(userId).isEmpty()) {

      persistenceService.clearConnectedFlag(userId);
      return;
    }
    syncUserInternal(userId, TRIGGER_SCHEDULED);
  }

  @Transactional(readOnly = true)
  public Map<LocalDate, GoogleCalendarBusyDay> findBusyDaysByUserId(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    return GoogleCalendarBusyMapper.indexBusyDays(
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            userId,
            startDate,
            endDate));
  }

  @Transactional(readOnly = true)
  public Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> findBusyDaysByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> result = new HashMap<>();
    for (GoogleCalendarBusyDay day : busyDayRepository
        .findByUser_IdInAndScheduleDateBetweenOrderByScheduleDateAsc(
            userIds,
            startDate,
            endDate)) {
      result
          .computeIfAbsent(day.getUser().getId(), ignored -> new HashMap<>())
          .put(day.getScheduleDate(), day);
    }
    return result;
  }

  private void syncUserInternal(UUID userId, String trigger) {
    GoogleCalendarCredential credential = credentialRepository.findByUser_Id(userId).orElse(null);
    if (credential == null) {

      return;
    }
    LocalDate windowStart = LocalDate.now(SEOUL);
    LocalDate windowEnd =
        ScheduleService.resolveCalendarWindowEnd(
            windowStart,
            tripMemberRepository.findMaxOngoingEndRangeByUserId(userId));
    try {
      AccessTokenResolution resolution = resolveAccessToken(credential);
      Instant timeMin = windowStart.atStartOfDay(SEOUL).toInstant();
      Instant timeMax = windowEnd.plusDays(1).atStartOfDay(SEOUL).toInstant();
      List<GoogleFreeBusyInterval> intervals =
          googleCalendarOAuthClient.queryFreeBusy(
              userId,
              resolution.accessToken(),
              timeMin,
              timeMax);
      persistenceService.applySyncSuccess(userId, resolution, windowStart, windowEnd, intervals);
    } catch (GoogleCalendarAuthException exception) {

      SocialIntegrationLog.warn(
          log,
          syncContext(userId, trigger),
          "Google Calendar sync failed permanently. disconnecting",
          exception);
      persistenceService.disconnectGoogleCalendar(userId);
    } catch (Exception exception) {

      SocialIntegrationLog.warn(
          log,
          syncContext(userId, trigger),
          "Google Calendar sync failed",
          exception);
      persistenceService.applySyncError(userId, PiiMasker.mask(exception.getMessage()));
    }
  }

  private SocialLogContext connectContext(UUID userId) {
    return SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_CONNECT)
        .withUserId(userId);
  }

  private SocialLogContext syncContext(UUID userId, String trigger) {
    return SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
        .withUserId(userId)
        .withTrigger(trigger);
  }

  private AccessTokenResolution resolveAccessToken(GoogleCalendarCredential credential) {
    if (credential.getAccessTokenCiphertext() != null
        && credential.getAccessTokenExpiresAt() != null
        && credential.getAccessTokenExpiresAt().isAfter(Instant.now())) {
      return new AccessTokenResolution(
          tokenCrypto.decrypt(credential.getAccessTokenCiphertext()), null, null, null);
    }
    String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
    GoogleOAuthTokenResponse refreshed = googleCalendarOAuthClient.refreshAccessToken(refreshToken);
    String accessCiphertext = tokenCrypto.encrypt(refreshed.accessToken());
    String refreshedRefreshCiphertext =
        (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank())
            ? tokenCrypto.encrypt(refreshed.refreshToken())
            : null;
    return new AccessTokenResolution(
        refreshed.accessToken(),
        accessCiphertext,
        refreshed.accessTokenExpiresAt(),
        refreshedRefreshCiphertext);
  }

}
