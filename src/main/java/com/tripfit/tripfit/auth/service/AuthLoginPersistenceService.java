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

  // 1. 전달받은 소셜 프로필 정보를 기반으로 유저를 생성하거나 기존 유저를 업데이트합니다.
  // 2. 해당 유저에 대한 새로운 Refresh Token을 발급하여 함께 반환합니다.
  @Transactional
  public Result persist(OAuthProfile profile) {
    User user = upsertUser(profile);
    IssuedRefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new Result(user, refreshToken);
  }

  // DB에서 소셜 제공자와 ID로 유저를 조회한 뒤,
  // 존재하면 기존 유저 정보를 갱신(소프트 딜리트 복구 포함)하고, 없다면 새로 생성하여 저장합니다.
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
