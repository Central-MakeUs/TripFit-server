package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// login · GET /auth/me · PATCH profile 응답용 UserSummary 조립
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
}
