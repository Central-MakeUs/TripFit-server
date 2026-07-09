package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
import com.tripfit.tripfit.user.dto.UpdateOnboardingRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private final UserRepository userRepository;

  public UserProfileService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // JWT 사용자의 성·이름 프로필을 저장함
  @Transactional
  public UserSummaryResponse updateProfile(Long userId, UpdateProfileRequest request) {
    User user = findUser(userId);
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    return UserSummaryMapper.toSummary(user);
  }

  // JWT 사용자의 마이페이지에서 성·이름을 수정함
  @Transactional
  public UserSummaryResponse updateMyPage(Long userId, UpdateMyPageRequest request) {
    return updateProfile(userId, new UpdateProfileRequest(request.firstName(), request.lastName()));
  }

  // JWT 사용자의 선택 온보딩 boolean을 partial update함
  @Transactional
  public UserSummaryResponse updateOnboarding(Long userId, UpdateOnboardingRequest request) {
    User user = findUser(userId);
    if (request.isGoogleCalendarConnected() != null) {
      user.setGoogleCalendarConnected(request.isGoogleCalendarConnected());
    }
    if (request.isScheduleRegistered() != null) {
      user.setScheduleRegistered(request.isScheduleRegistered());
    }
    if (request.isOptionalOnboardingCompleted() != null) {
      user.setOptionalOnboardingCompleted(request.isOptionalOnboardingCompleted());
    }
    return UserSummaryMapper.toSummary(user);
  }

  // wave 2+ 핵심 API에서 성·이름 미입력 시 호출함
  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
