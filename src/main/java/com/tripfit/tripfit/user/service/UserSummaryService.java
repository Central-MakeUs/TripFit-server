package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// login · GET /auth/me · PATCH profile 응답용 UserSummary + 방 입장 조건 검사
@Service
public class UserSummaryService {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserLookupService userLookupService;

  public UserSummaryService(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      UserLookupService userLookupService) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.userLookupService = userLookupService;
  }

  // User → UserSummary DTO. hasPreSchedule은 정기/개인 일정 EXISTS로 매번 계산
  @Transactional(readOnly = true)
  public UserSummaryResponse toSummary(User user) {
    return new UserSummaryResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getProvider(),
        user.isGoogleCalendarConnected(),
        hasPreSchedule(user.getId()),
        user.isAllFree());
  }

  // 파생: regular OR personal row EXISTS (user 컬럼 아님)
  @Transactional(readOnly = true)
  public boolean hasPreSchedule(UUID userId) {
    return regularScheduleRepository.existsByUserId(userId)
        || personalScheduleRepository.existsByUserId(userId);
  }

  // 방 입장 가능 여부 — 정기≥1 OR 개별≥1 OR 전부 free
  @Transactional(readOnly = true)
  public boolean canEnterRoom(User user) {
    return user.isAllFree() || hasPreSchedule(user.getId());
  }

  // 입장 조건 미충족 시 SCHEDULE_ENTRY_REQUIRED
  public void requireCanEnterRoom(User user) {
    if (!canEnterRoom(user)) {
      throw new TripFitException(UserErrorCode.SCHEDULE_ENTRY_REQUIRED);
    }
  }

  // @TripMemberOnly / @TripOwnerOnly 인터셉터용 — userId로 로드 후 게이트
  public void requireCanEnterRoom(UUID userId) {
    requireCanEnterRoom(userLookupService.requireUser(userId));
  }

  // Skip+0행 / create·join — 일정 없으면 is_all_free=true (이미 일정이면 유지)
  public void markAllFreeIfNoSchedules(User user) {
    if (!hasPreSchedule(user.getId())) {
      user.setAllFree(true);
    }
  }

  // 일정이 한 건이라도 생기면 전부 free 선언을 해제한다
  public void clearAllFreeOnScheduleAdded(User user) {
    if (user.isAllFree()) {
      user.setAllFree(false);
    }
  }

  // 일정 CLEAR 후 둘 다 0행 → is_all_free=true
  public void markAllFreeIfSchedulesCleared(User user) {
    if (!hasPreSchedule(user.getId())) {
      user.setAllFree(true);
    }
  }
}
