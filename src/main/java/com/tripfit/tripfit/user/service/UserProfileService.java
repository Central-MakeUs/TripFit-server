package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.OnboardingNameRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class UserProfileService {

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  @Transactional
  public UserSummaryResponse registerOnboardingName(UUID userId, OnboardingNameRequest request) {
    User user = userLookupService.requireUser(userId);
    user.applyProfilePatch(request.firstName().trim(), request.lastName().trim(), null);
    return userSummaryService.toSummary(user);
  }

  @Transactional
  public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    if (request.firstName() == null
        && request.lastName() == null
        && request.notificationEnabled() == null) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT, "최소 1개 필드가 필요합니다.");
    }

    User user = userLookupService.requireUser(userId);
    user.applyProfilePatch(
        request.firstName() != null ? requireNonBlank(request.firstName()) : null,
        request.lastName() != null ? requireNonBlank(request.lastName()) : null,
        request.notificationEnabled());
    return userSummaryService.toSummary(user);
  }

  private String requireNonBlank(String value) {
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT, "이름은 공백일 수 없습니다.");
    }
    return trimmed;
  }

  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }
}
