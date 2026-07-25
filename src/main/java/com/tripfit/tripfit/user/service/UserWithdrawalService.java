package com.tripfit.tripfit.user.service;

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

@Service
// 회원 탈퇴 유스케이스 — cascade(참여 방 나가기·소유 방 삭제) → 개인 데이터 hard delete → User soft delete·PII 스크럽
public class UserWithdrawalService {

  private final UserLookupService userLookupService;

  private final TripService tripService;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  private final GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  private final RefreshTokenService refreshTokenService;

  public UserWithdrawalService(
      UserLookupService userLookupService,
      TripService tripService,
      PersonalScheduleRepository personalScheduleRepository,
      RegularScheduleRepository regularScheduleRepository,
      GoogleCalendarCredentialRepository googleCalendarCredentialRepository,
      GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository,
      RefreshTokenService refreshTokenService) {
    this.userLookupService = userLookupService;
    this.tripService = tripService;
    this.personalScheduleRepository = personalScheduleRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.googleCalendarCredentialRepository = googleCalendarCredentialRepository;
    this.googleCalendarBusyDayRepository = googleCalendarBusyDayRepository;
    this.refreshTokenService = refreshTokenService;
  }

  // 차단 없이 항상 진행 — 참여 방 자동 나가기·소유 방 자동 삭제 후 개인 데이터 hard delete, User는 soft delete+PII 스크럽
  @Transactional
  public void withdraw(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {
      // 이미 탈퇴한 계정(액세스 토큰 만료 전 재호출) — 중복 처리 없이 idempotent 종료
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
