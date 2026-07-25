package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
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

  // 온보딩 프로필(성·이름) 저장
  @Transactional
  public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    User user = userLookupService.requireUser(userId);
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    // hasPreSchedule은 userSummaryService가 일정 테이블 EXISTS로 매번 파생
    return userSummaryService.toSummary(user);
  }

  // 마이페이지 성·이름 수정 — updateProfile과 동일 저장
  @Transactional
  public UserSummaryResponse updateMyPage(UUID userId, UpdateMyPageRequest request) {
    return updateProfile(userId, new UpdateProfileRequest(request.firstName(), request.lastName()));
  }

  // 성·이름 미입력이면 trip 생성·참여 등에서 PROFILE_NAME_REQUIRED
  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }
}
