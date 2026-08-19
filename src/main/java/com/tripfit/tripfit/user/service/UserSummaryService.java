package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// login · GET /auth/me · PATCH profile 응답용 UserSummary 조립
@Service
public class UserSummaryService {

  // User → UserSummary DTO. hasCompletedPreSchedule만 파생이며 users 행 안에서 끝나 추가 조회가 없다
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
        user.hasCompletedPreSchedule(),
        user.isNotificationEnabled());
  }
}
