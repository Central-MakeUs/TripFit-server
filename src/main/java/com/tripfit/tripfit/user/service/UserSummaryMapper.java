package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;

public final class UserSummaryMapper {

  private UserSummaryMapper() {}

  public static UserSummaryResponse toSummary(User user) {
    return new UserSummaryResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getProvider(),
        user.isGoogleCalendarConnected(),
        user.isScheduleRegistered(),
        user.isOptionalOnboardingCompleted());
  }
}
