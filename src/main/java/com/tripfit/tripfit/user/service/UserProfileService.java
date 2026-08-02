package com.tripfit.tripfit.user.service;

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
    applyName(user, request.firstName(), request.lastName());
    // hasPreSchedule은 userSummaryService가 일정 테이블 EXISTS로 매번 파생
    return userSummaryService.toSummary(user);
  }

  // 마이페이지 성·이름 수정 — 온보딩 등록과 동일 컬럼 갱신
  @Transactional
  public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    User user = userLookupService.requireUser(userId);
    applyName(user, request.firstName(), request.lastName());
    return userSummaryService.toSummary(user);
  }

  private void applyName(User user, String firstName, String lastName) {
    user.setFirstName(firstName.trim());
    user.setLastName(lastName.trim());
  }

  // 성·이름 미입력이면 trip 생성·참여 등에서 PROFILE_NAME_REQUIRED
  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }
}
