package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class UserLookupService {

  private final UserRepository userRepository;

  public User requireUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }

  // 탈퇴하지 않은 사용자를 행 잠금과 함께 찾는다. 트랜잭션 안에서 불러야 하며, 커밋할 때까지 같은 사용자의 탈퇴가 기다린다.
  // 백그라운드 작업이 탈퇴와 엇갈려 탈퇴한 사용자에게 값을 저장하지 않도록 할 때 쓴다.
  public Optional<User> findActiveUserForUpdate(UUID userId) {
    return userRepository.findByIdForUpdate(userId).filter(user -> user.getDeletedAt() == null);
  }
}
