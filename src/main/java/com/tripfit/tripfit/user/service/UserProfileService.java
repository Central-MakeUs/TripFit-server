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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private final UserRepository userRepository;

  public UserProfileService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    User user = findUser(userId);
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    return UserSummaryMapper.toSummary(user);
  }

  @Transactional
  public UserSummaryResponse updateMyPage(UUID userId, UpdateMyPageRequest request) {
    return updateProfile(userId, new UpdateProfileRequest(request.firstName(), request.lastName()));
  }

  @Transactional
  public UserSummaryResponse updateOnboarding(UUID userId, UpdateOnboardingRequest request) {
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

  // BR-USER-001: wave 2+ 핵심 API(여행방 생성 등) 진입 전 성·이름 완료 강제
  public void requireProfileNameComplete(User user) {
    if (!user.hasProfileNameComplete()) {
      throw new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED);
    }
  }

  private User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
