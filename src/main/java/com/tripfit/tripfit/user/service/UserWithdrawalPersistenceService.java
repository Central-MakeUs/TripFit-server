package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.RefreshTokenService;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  // 회원 탈퇴 시 관련된 모든 내부 엔티티를 정리합니다.
  // 트랜잭션 단위로 묶여 있어 부분 실패 시 롤백됩니다.
  @Transactional
  public void finalizeWithdrawal(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {

      return;
    }

    // 1. 참여 중인 모든 여행방에서 나가기 처리(방장인 경우 방 삭제)
    tripService.leaveAllActiveTripsAsMember(userId);
    tripService.deleteAllOwnedActiveTrips(userId);

    // 2. 개인 일정, 정기 일정, 구글 캘린더 관련 메타데이터 및 인증 토큰 삭제
    personalScheduleRepository.deleteByUserId(userId);
    regularScheduleRepository.deleteByUserId(userId);
    googleCalendarCredentialRepository.deleteByUser_Id(userId);
    googleCalendarBusyDayRepository.deleteByUser_Id(userId);
    refreshTokenService.revokeAllForUser(userId);

    // 3. 유저 엔티티 소프트 딜리트 처리 및 PII(개인식별정보) 초기화
    user.scrubPiiForWithdrawal();
  }
}
