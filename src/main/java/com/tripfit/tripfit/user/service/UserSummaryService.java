package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSummaryService {

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
