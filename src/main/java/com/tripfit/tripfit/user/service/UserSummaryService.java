package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSummaryService {

  // User 엔티티 데이터를 기반으로 UserSummaryResponse(요약 객체)를 생성하여 반환합니다.
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
