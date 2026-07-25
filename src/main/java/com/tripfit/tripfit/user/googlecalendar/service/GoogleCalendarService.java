package com.tripfit.tripfit.user.googlecalendar.service;

import com.tripfit.tripfit.common.exception.TripFitException;
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
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarBusyMapper.SlotBusyFlags;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final GoogleCalendarOAuthClient googleCalendarOAuthClient;

  private final GoogleCalendarTokenCrypto tokenCrypto;

  private final GoogleCalendarCredentialRepository credentialRepository;

  private final GoogleCalendarBusyDayRepository busyDayRepository;

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  public GoogleCalendarService(
      GoogleCalendarOAuthClient googleCalendarOAuthClient,
      GoogleCalendarTokenCrypto tokenCrypto,
      GoogleCalendarCredentialRepository credentialRepository,
      GoogleCalendarBusyDayRepository busyDayRepository,
      UserLookupService userLookupService,
      UserSummaryService userSummaryService) {
    this.googleCalendarOAuthClient = googleCalendarOAuthClient;
    this.tokenCrypto = tokenCrypto;
    this.credentialRepository = credentialRepository;
    this.busyDayRepository = busyDayRepository;
    this.userLookupService = userLookupService;
    this.userSummaryService = userSummaryService;
  }

  // authorization code로 연동 — credential 저장·flag=true·즉시 1회 sync
  @Transactional
  public UserSummaryResponse connect(UUID userId, String authorizationCode) {
    User user = userLookupService.requireUser(userId);
    GoogleOAuthTokenResponse tokens;
    try {
      tokens = googleCalendarOAuthClient.exchangeAuthorizationCode(authorizationCode);
    } catch (GoogleCalendarAuthException exception) {
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);
    }

    String refreshCiphertext = tokenCrypto.encrypt(tokens.refreshToken());
    String accessCiphertext = tokenCrypto.encrypt(tokens.accessToken());
    String googleAccountEmail =
        googleCalendarOAuthClient.fetchGoogleAccountEmail(tokens.accessToken());

    GoogleCalendarCredential credential =
        credentialRepository
            .findByUser_Id(userId)
            .map(
                existing -> {
                  existing.updateTokens(
                      refreshCiphertext,
                      accessCiphertext,
                      tokens.accessTokenExpiresAt(),
                      googleAccountEmail);
                  return existing;
                })
            .orElseGet(
                () -> GoogleCalendarCredential.create(
                    user,
                    refreshCiphertext,
                    accessCiphertext,
                    tokens.accessTokenExpiresAt(),
                    googleAccountEmail));

    credentialRepository.save(credential);
    user.setGoogleCalendarConnected(true);
    syncUserInternal(user, credential);
    return userSummaryService.toSummary(user);
  }

  // 의도적 해제 — revoke(best-effort)·credential·busy_day 삭제·flag=false (수동 일정 유지)
  @Transactional
  public UserSummaryResponse disconnect(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (!user.isGoogleCalendarConnected()) {
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED);
    }
    credentialRepository
        .findByUser_Id(userId)
        .ifPresent(
            credential -> {
              String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
              googleCalendarOAuthClient.revokeRefreshToken(refreshToken);
            });
    clearGoogleLayer(userId);
    user.setGoogleCalendarConnected(false);
    return userSummaryService.toSummary(user);
  }

  // freeBusy → busy_day 갱신 (C1 윈도우) — 권한 영구 실패 시 flag=false·Google 레이어 정리
  @Transactional
  public void syncUser(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (!user.isGoogleCalendarConnected()) {
      return;
    }
    GoogleCalendarCredential credential =
        credentialRepository
            .findByUser_Id(userId)
            .orElseGet(
                () -> {
                  user.setGoogleCalendarConnected(false);
                  return null;
                });
    if (credential == null) {
      return;
    }
    syncUserInternal(user, credential);
  }

  // 달력 Merge용 busy_day 조회 — userId·기간
  @Transactional(readOnly = true)
  public Map<LocalDate, GoogleCalendarBusyDay> findBusyDaysByUserId(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    return indexBusyDays(
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            userId,
            startDate,
            endDate));
  }

  // 멤버 달력 Merge용 busy_day batch 조회 — userId별 Map
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

  public static Map<LocalDate, GoogleCalendarBusyDay> indexBusyDays(
      List<GoogleCalendarBusyDay> days) {
    Map<LocalDate, GoogleCalendarBusyDay> byDate = new HashMap<>();
    for (GoogleCalendarBusyDay day : days) {
      byDate.put(day.getScheduleDate(), day);
    }
    return byDate;
  }

  private void syncUserInternal(User user, GoogleCalendarCredential credential) {
    LocalDate windowStart = LocalDate.now(SEOUL);
    LocalDate windowEnd =
        windowStart.plusYears(ScheduleService.CALENDAR_WINDOW_YEARS).minusDays(1);
    try {
      String accessToken = resolveAccessToken(credential);
      Instant timeMin = windowStart.atStartOfDay(SEOUL).toInstant();
      Instant timeMax = windowEnd.plusDays(1).atStartOfDay(SEOUL).toInstant();
      List<GoogleFreeBusyInterval> intervals =
          googleCalendarOAuthClient.queryFreeBusy(accessToken, timeMin, timeMax);
      replaceBusyDays(user, windowStart, windowEnd, intervals);
      credential.markSynced();
      credentialRepository.save(credential);
    } catch (GoogleCalendarAuthException exception) {
      handlePermanentAuthFailure(user);
    } catch (Exception exception) {
      credential.markSyncError(exception.getMessage());
      credentialRepository.save(credential);
    }
  }

  private String resolveAccessToken(GoogleCalendarCredential credential) {
    if (credential.getAccessTokenCiphertext() != null
        && credential.getAccessTokenExpiresAt() != null
        && credential.getAccessTokenExpiresAt().isAfter(Instant.now())) {
      return tokenCrypto.decrypt(credential.getAccessTokenCiphertext());
    }
    String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
    GoogleOAuthTokenResponse refreshed = googleCalendarOAuthClient.refreshAccessToken(refreshToken);
    String accessCiphertext = tokenCrypto.encrypt(refreshed.accessToken());
    credential.updateAccessTokenCache(accessCiphertext, refreshed.accessTokenExpiresAt());
    if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
      credential.setRefreshTokenCiphertext(tokenCrypto.encrypt(refreshed.refreshToken()));
    }
    credentialRepository.save(credential);
    return refreshed.accessToken();
  }

  private void replaceBusyDays(
      User user,
      LocalDate windowStart,
      LocalDate windowEnd,
      List<GoogleFreeBusyInterval> intervals) {
    UUID userId = user.getId();
    busyDayRepository.deleteByUser_IdAndScheduleDateBefore(userId, windowStart);
    busyDayRepository.deleteByUser_IdAndScheduleDateAfter(userId, windowEnd);

    Map<LocalDate, SlotBusyFlags> mapped = GoogleCalendarBusyMapper.mapIntervalsToDays(intervals);
    List<GoogleCalendarBusyDay> existing =
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            userId,
            windowStart,
            windowEnd);
    Map<LocalDate, GoogleCalendarBusyDay> existingByDate = indexBusyDays(existing);

    for (Map.Entry<LocalDate, SlotBusyFlags> entry : mapped.entrySet()) {
      LocalDate date = entry.getKey();
      SlotBusyFlags flags = entry.getValue();
      GoogleCalendarBusyDay day = existingByDate.remove(date);
      if (day == null) {
        busyDayRepository.save(GoogleCalendarBusyMapper.toEntity(user, date, flags));
      } else {
        day.apply(flags.isMorningBusy(), flags.isAfternoonBusy(), flags.isEveningBusy());
      }
    }
    busyDayRepository.deleteAll(existingByDate.values());
  }

  private void handlePermanentAuthFailure(User user) {
    clearGoogleLayer(user.getId());
    user.setGoogleCalendarConnected(false);
  }

  private void clearGoogleLayer(UUID userId) {
    credentialRepository.deleteByUser_Id(userId);
    busyDayRepository.deleteByUser_Id(userId);
  }

}
