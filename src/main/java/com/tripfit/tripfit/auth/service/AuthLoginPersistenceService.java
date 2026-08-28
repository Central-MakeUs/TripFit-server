package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthLoginPersistenceService {

  private final UserRepository userRepository;

  private final RefreshTokenService refreshTokenService;

  @Transactional
  public Result persist(OAuthProfile profile) {
    User user = upsertUser(profile);
    IssuedRefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new Result(user, refreshToken);
  }

  private User upsertUser(OAuthProfile profile) {
    Optional<User> existing =
        userRepository.findByProviderAndSocialId(profile.provider(), profile.providerUserId());
    return existing
        .map(user -> updateFromProfile(revive(user), profile))
        .orElseGet(() -> userRepository.save(createUserFromProfile(profile)));
  }

  private User revive(User user) {
    user.reviveIfWithdrawn();
    return user;
  }

  private User createUserFromProfile(OAuthProfile profile) {
    return new User(
        profile.providerUserId(),
        profile.provider(),
        profile.email(),
        profile.nickname(),
        profile.profileImageUrl());
  }

  private User updateFromProfile(User user, OAuthProfile profile) {
    user.applySocialProfile(profile.email(), profile.nickname(), profile.profileImageUrl());
    return user;
  }

  public record Result(
      User user,
      IssuedRefreshToken refreshToken
  ) {
  }
}
