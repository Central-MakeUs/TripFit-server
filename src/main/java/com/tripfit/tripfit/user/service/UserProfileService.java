package com.tripfit.tripfit.user.service;

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
// 성·이름 PATCH 및 trip 핵심 API 진입 전 이름 완료 검증 — UserSummary는 UserSummaryService에 위임
public class UserProfileService {

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  public UserProfileService(
      UserLookupService userLookupService, UserSummaryService userSummaryService) {
    this.userLookupService = userLookupService;
    this.userSummaryService = userSummaryService;
  }

  // 온보딩 최초 성·이름 등록
  @Transactional
  public UserSummaryResponse registerOnboardingName(UUID userId, OnboardingNameRequest request) {
    User user = userLookupService.requireUser(userId);
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    // hasPreSchedule은 userSummaryService가 일정 테이블 EXISTS로 매번 파생
    return userSummaryService.toSummary(user);
  }

  // 마이페이지 프로필 부분 수정 — firstName/lastName/notificationEnabled 중 포함된 필드만 갱신(D8)
  @Transactional
  public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    if (request.firstName() == null
        && request.lastName() == null
        && request.notificationEnabled() == null) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT, "최소 1개 필드가 필요합니다.");
    }

    User user = userLookupService.requireUser(userId);
    if (request.firstName() != null) {
      user.setFirstName(requireNonBlank(request.firstName()));
    }
    if (request.lastName() != null) {
      user.setLastName(requireNonBlank(request.lastName()));
    }
    if (request.notificationEnabled() != null) {
      user.setNotificationEnabled(request.notificationEnabled());
    }
    return userSummaryService.toSummary(user);
  }

  private String requireNonBlank(String value) {
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT, "이름은 공백일 수 없습니다.");
    }
    return trimmed;
  }

  // 성·이름 미입력이면 trip 생성·참여 등에서 PROFILE_NAME_REQUIRED
  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }
}
