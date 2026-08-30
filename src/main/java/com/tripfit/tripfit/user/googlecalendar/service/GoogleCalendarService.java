package com.tripfit.tripfit.user.googlecalendar.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.PiiMasker;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
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
public class GoogleCalendarService {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // syncUserInternal 실패 로그의 trigger 필드 — 어느 경로로 sync가 시작됐는지
  // 구분(docs/specs/social-integration-structured-logging.md)
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

  public GoogleCalendarService(
      GoogleCalendarOAuthClient googleCalendarOAuthClient,
      SocialTokenCrypto tokenCrypto,
      GoogleCalendarCredentialRepository credentialRepository,
      GoogleCalendarBusyDayRepository busyDayRepository,
      UserLookupService userLookupService,
      UserSummaryService userSummaryService,
      TripMemberRepository tripMemberRepository,
      GoogleCalendarSyncPersistenceService persistenceService) {
    this.googleCalendarOAuthClient = googleCalendarOAuthClient;
    this.tokenCrypto = tokenCrypto;
    this.credentialRepository = credentialRepository;
    this.busyDayRepository = busyDayRepository;
    this.userLookupService = userLookupService;
    this.userSummaryService = userSummaryService;
    this.tripMemberRepository = tripMemberRepository;
    this.persistenceService = persistenceService;
  }

  // authorization code로 연동 — Google 서버와의 통신(코드 교환·freeBusy 조회)을 먼저 끝내고, DB 쓰기(credential
  // 저장·flag=true)는 persistenceService의 짧은 트랜잭션에 위임한다 (auth 도메인 AuthService 패턴과 동일 —
  // docs/audits/user/audit.md A-1)
  public UserSummaryResponse connect(UUID userId, String authorizationCode, String redirectUri) {
    userLookupService.requireUser(userId);
    GoogleOAuthTokenResponse tokens;
    boolean hasRedirectUri = redirectUri != null && !redirectUri.isBlank();
    try {
      tokens = googleCalendarOAuthClient.exchangeAuthorizationCode(authorizationCode, redirectUri);
    } catch (GoogleCalendarAuthException exception) {
      // 원인(HTTP status·Google 에러 body)을 로그로 남겨야 진단 가능 — GlobalExceptionHandler는
      // TripFitException을 로깅하지 않으므로 여기서 남기지 않으면 실패 원인이 완전히 유실된다
      SocialIntegrationLog.warn(
          log,
          connectContext(userId),
          "Google Calendar connect failed — authorization code exchange error (hasRedirectUri="
              + hasRedirectUri
              + ")",
          exception);
      throw new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED);
    } catch (TripFitException exception) {
      // exchangeAuthorizationCode()가 토큰 응답에 refresh_token이 없을 때 여기로 온다(같은 Google 계정을
      // 다른 client_id 재동의 없이 재연동하면 Google이 refresh_token을 생략) — 이 분기도 로그 없이 삼켜지고
      // 있었음. hasRedirectUri로 네이티브(false)/브라우저(true) 경로를 구분해 재현 조건을 좁힐 수 있게 한다
      SocialIntegrationLog.warn(
          log,
          connectContext(userId),
          "Google Calendar connect failed — token response missing refresh_token (hasRedirectUri="
              + hasRedirectUri
              + ")");
      throw exception;
    }
    if (tokens.scope() != null) {
      // 콘솔·FE 설정이 맞다고 확인돼도 실제 발급된 토큰의 스코프를 재현 없이 확인할 수 있게 함
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

  // 의도적 해제 — revoke(best-effort, HTTP)를 먼저 트랜잭션 밖에서 끝내고, credential·busy_day 삭제·flag=false
  // (수동 일정 유지)는 persistenceService의 짧은 트랜잭션에 위임한다 (connect()/syncUser()와 동일 패턴 — A-1 round2)
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
              googleCalendarOAuthClient.revokeRefreshToken(userId, refreshToken);
            });
    persistenceService.disconnectGoogleCalendar(userId);
    User updated = userLookupService.requireUser(userId);
    return userSummaryService.toSummary(updated);
  }

  // freeBusy → busy_day 갱신 (C1 윈도우) — 권한 영구 실패 시 flag=false·Google 레이어 정리. Google 서버와의
  // 통신은 트랜잭션 밖에서 수행 (syncUserInternal 참고 — A-1)
  public void syncUser(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (!user.isGoogleCalendarConnected()) {
      return;
    }
    if (credentialRepository.findByUser_Id(userId).isEmpty()) {
      // credential row는 없는데 flag만 true로 남은 데이터 불일치
      persistenceService.clearConnectedFlag(userId);
      return;
    }
    syncUserInternal(userId, TRIGGER_SCHEDULED);
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

  // Google 서버와의 통신(access token 갱신·freeBusy 조회)을 끝낸 뒤, 결과만 persistenceService의 짧은
  // 트랜잭션에 반영한다 — 이 메서드 자체는 트랜잭션을 열지 않는다(A-1)
  private void syncUserInternal(UUID userId, String trigger) {
    GoogleCalendarCredential credential = credentialRepository.findByUser_Id(userId).orElse(null);
    if (credential == null) {
      // connect() 직후 disconnect()가 먼저 끝난 race — 반영할 대상 없음
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
      // 401·invalid_grant 등 진짜 권한 실패만 이 분기로 온다(client가 분류) — connect() 직후 1회 sync도 이
      // 메서드를 타므로, 일시적 실패까지 여기서 잡으면 방금 저장한 credential이 곧바로 삭제된다
      SocialIntegrationLog.warn(
          log,
          syncContext(userId, trigger),
          "Google Calendar sync failed permanently — disconnecting",
          exception);
      persistenceService.disconnectGoogleCalendar(userId);
    } catch (Exception exception) {
      // freeBusy·refresh 등 일시적 오류 — 30분 폴링이 자동 재시도하므로 credential은 보존하지만, 원인 없이
      // markSyncError 메시지만 DB에 남으면 재현 없이는 언제·누구에게 발생했는지 알 수 없다
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

  // access token 캐시가 유효하면 그대로 쓰고, 만료됐으면 refresh — DB에는 쓰지 않고 결과만 반환한다(반영은
  // persistenceService.applySyncSuccess의 짧은 트랜잭션에서, B-2)
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
