package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.RefreshTokenService;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// UserWithdrawalService.withdraw()가 소셜 provider revoke(외부 HTTP 최대 4회, best-effort)를 먼저 끝낸
// 뒤에만 호출하는 DB 쓰기 전용 계층 — cascade·개인 데이터 hard delete·User soft delete를 하나의 짧은
// 트랜잭션으로 묶는다 (A-2, GoogleCalendarSyncPersistenceService와 동일 패턴)
@Service
@RequiredArgsConstructor
public class UserWithdrawalPersistenceService {

  private final UserLookupService userLookupService;

  private final TripService tripService;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  private final GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  private final RefreshTokenService refreshTokenService;

  // cascade(참여 방 나가기·소유 방 삭제) → 개인 데이터 hard delete → User soft delete+PII 스크럽을 원자적으로 처리
  @Transactional
  public void finalizeWithdrawal(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {
      // provider revoke 대기 중 동시 탈퇴 요청이 먼저 끝난 race — 중복 처리 없이 idempotent 종료
      return;
    }

    // 1. cascade: MEMBER인 활성 방 전부 나가기 → OWNER인 활성 방 전부 삭제
    tripService.leaveAllActiveTripsAsMember(userId);
    tripService.deleteAllOwnedActiveTrips(userId);

    // 2. 개인 전용 데이터 hard delete
    personalScheduleRepository.deleteByUserId(userId);
    regularScheduleRepository.deleteByUserId(userId);
    googleCalendarCredentialRepository.deleteByUser_Id(userId);
    googleCalendarBusyDayRepository.deleteByUser_Id(userId);
    refreshTokenService.revokeAllForUser(userId);

    // 3. User soft delete + PII 스크럽 — socialId·provider·id는 FK 무결성·재로그인 차단 판별을 위해 유지
    user.setDeletedAt(LocalDateTime.now());
    user.setEmail(null);
    user.setFirstName(null);
    user.setLastName(null);
    user.setNickname(null);
    user.setProfileImageUrl(null);
    user.setGoogleCalendarConnected(false);
  }
}
