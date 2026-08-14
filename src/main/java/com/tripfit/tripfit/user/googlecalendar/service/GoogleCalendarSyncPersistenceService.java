package com.tripfit.tripfit.user.googlecalendar.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarBusyMapper.SlotBusyFlags;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// GoogleCalendarService가 구글 서버와 순차로 여러 번 통신하는 동안(authorization code 교환·freeBusy 조회 최대
// 9청크) DB 접속을 붙잡지 않도록, 통신이 끝난 뒤 결과만 짧게 반영하는 DB 쓰기 전용 계층
// (auth 도메인 AuthLoginPersistenceService와 동일 패턴)
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncPersistenceService {

  private final GoogleCalendarCredentialRepository credentialRepository;

  private final GoogleCalendarBusyDayRepository busyDayRepository;

  private final UserLookupService userLookupService;

  // authorization code 교환 결과(토큰 암호문)를 credential에 반영하고 연동 플래그를 켠다
  @Transactional
  public void saveConnectedCredential(
      UUID userId,
      String refreshCiphertext,
      String accessCiphertext,
      Instant accessTokenExpiresAt,
      String googleAccountEmail) {
    User user = userLookupService.requireUser(userId);
    GoogleCalendarCredential credential =
        credentialRepository
            .findByUser_Id(userId)
            .map(
                existing -> {
                  existing.updateTokens(
                      refreshCiphertext,
                      accessCiphertext,
                      accessTokenExpiresAt,
                      googleAccountEmail);
                  return existing;
                })
            .orElseGet(
                () -> GoogleCalendarCredential.create(
                    user,
                    refreshCiphertext,
                    accessCiphertext,
                    accessTokenExpiresAt,
                    googleAccountEmail));
    // 신규 엔티티일 수 있어 명시적 save 필요 — 기존 행 업데이트는 managed 상태라 dirty checking으로 충분
    credentialRepository.save(credential);
    user.connectGoogleCalendar();
  }

  // freeBusy 조회 성공 결과를 busy_day에 반영하고 credential 캐시·sync 상태를 갱신한다. 파라미터로 받은
  // credential·user는 이전 트랜잭션에서 detach됐을 수 있어 여기서 다시 조회해 관리 상태로 만든다
  @Transactional
  public void applySyncSuccess(
      UUID userId,
      AccessTokenResolution resolution,
      LocalDate windowStart,
      LocalDate windowEnd,
      List<GoogleFreeBusyInterval> intervals) {
    GoogleCalendarCredential credential = credentialRepository.findByUser_Id(userId).orElse(null);
    if (credential == null) {
      // 통신 중 disconnect()가 먼저 끝나 credential이 사라진 race — 반영할 대상 없음
      return;
    }
    if (resolution.refreshedAccessCiphertext() != null) {
      credential.updateAccessTokenCache(
          resolution.refreshedAccessCiphertext(),
          resolution.refreshedAccessExpiresAt());
      if (resolution.refreshedRefreshCiphertext() != null) {
        credential.applyRotatedRefreshToken(resolution.refreshedRefreshCiphertext());
      }
    }
    replaceBusyDays(credential.getUser(), windowStart, windowEnd, intervals);
    credential.markSynced();
  }

  // 일시적 sync 오류 — credential은 유지하고 오류 메시지만 남김(30분 폴링이 자동 재시도)
  @Transactional
  public void applySyncError(UUID userId, String maskedMessage) {
    credentialRepository
        .findByUser_Id(userId)
        .ifPresent(credential -> credential.markSyncError(maskedMessage));
  }

  // Google 레이어 전체 삭제 + 연동 플래그 해제 — 사용자 명시적 해제(disconnect)와 권한 영구 실패(401·invalid_grant)
  // 양쪽에서 재사용하는 동일한 DB 정리 로직(revoke HTTP는 호출부 책임, 이 메서드는 DB 쓰기만)
  @Transactional
  public void disconnectGoogleCalendar(UUID userId) {
    credentialRepository.deleteByUser_Id(userId);
    busyDayRepository.deleteByUser_Id(userId);
    userLookupService.requireUser(userId).disconnectGoogleCalendar();
  }

  // credential row는 없는데 flag만 true로 남은 데이터 불일치 복구
  @Transactional
  public void clearConnectedFlag(UUID userId) {
    userLookupService.requireUser(userId).disconnectGoogleCalendar();
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
    Map<LocalDate, GoogleCalendarBusyDay> existingByDate =
        GoogleCalendarService.indexBusyDays(existing);

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
}
