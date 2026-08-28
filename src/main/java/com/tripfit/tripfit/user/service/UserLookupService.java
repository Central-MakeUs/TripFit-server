package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
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
}
