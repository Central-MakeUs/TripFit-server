package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserSummaryService {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserLookupService userLookupService;

  // User → UserSummary DTO. hasRegularSchedule·hasPreSchedule은 일정 EXISTS로 매번 계산
  @Transactional(readOnly = true)
  public UserSummaryResponse toSummary(User user) {
    // regular EXISTS를 한 번만 조회해 두 파생 필드에 함께 쓴다 (hasPreSchedule = regular OR personal)
    boolean hasRegular = hasRegularSchedule(user.getId());
    return new UserSummaryResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getProvider(),
        user.isGoogleCalendarConnected(),
        hasRegular,
        hasRegular || personalScheduleRepository.existsByUserId(user.getId()),
        user.isAllFree(),
        user.isNotificationEnabled());
  }

  // 파생: regular row EXISTS만 — 일정 확인 화면의 정기 일정 유무 분기용 (개별 일정은 제외)
  @Transactional(readOnly = true)
  public boolean hasRegularSchedule(UUID userId) {
    return regularScheduleRepository.existsByUserId(userId);
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

  // Skip+0행 / create·join / 일정 CLEAR 후 — 일정이 없으면 is_all_free=true (이미 일정이면 유지)
  public void markAllFreeIfNoSchedules(User user) {
    if (!hasPreSchedule(user.getId())) {
      user.applyAllFree(true);
    }
  }

  // 일정이 한 건이라도 생기면 전부 free 선언을 해제한다
  public void clearAllFreeOnScheduleAdded(User user) {
    if (user.isAllFree()) {
      user.applyAllFree(false);
    }
  }
}
