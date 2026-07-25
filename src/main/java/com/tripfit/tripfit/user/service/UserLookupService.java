package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
// userId로 User를 로드하는 공통 진입점 — 여러 서비스가 각자 findById+orElseThrow를 반복하지 않도록 통일
public class UserLookupService {

  private final UserRepository userRepository;

  public UserLookupService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // userId로 User 조회 — 없으면 AUTH_FORBIDDEN(인증은 됐으나 대상 계정 없음)
  public User requireUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
