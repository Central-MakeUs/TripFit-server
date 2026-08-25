package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 흐름의 DB 쓰기(user upsert·refresh token 발급)만 담당하는 짧은 트랜잭션 — AuthService.login()이 소셜
// provider 검증·authorizationCode 교환 같은 외부 HTTP 호출을 이 트랜잭션 밖에서 먼저 끝내도록 분리한 것.
// AuthService의 private 메서드로 두면 self-invocation 때문에 @Transactional 프록시가 안 걸려 별도 빈으로 둔다
@Service
public class AuthLoginPersistenceService {

  private final UserRepository userRepository;

  private final RefreshTokenService refreshTokenService;

  public AuthLoginPersistenceService(
      UserRepository userRepository, RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.refreshTokenService = refreshTokenService;
  }

  @Transactional
  public Result persist(OAuthProfile profile) {
    User user = upsertUser(profile);
    RefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new Result(user, refreshToken);
  }

  // 소셜 계정 기준으로 사용자를 조회하고 없으면 새로 생성함 — 탈퇴(soft-deleted) 계정은 부활시켜 재가입을 대체
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

  // 소셜 프로필로 신규 사용자 엔티티를 생성함
  private User createUserFromProfile(OAuthProfile profile) {
    return new User(
        profile.providerUserId(),
        profile.provider(),
        profile.email(),
        profile.nickname(),
        profile.profileImageUrl());
  }

  // 재로그인 시 소셜에서 온 이메일·닉네임·프로필 이미지만 갱신 — 성·이름은 PATCH profile 전용
  // profileImageUrl: 현재는 provider URL 그대로. S3 미러는 추후
  private User updateFromProfile(User user, OAuthProfile profile) {
    if (profile.email() != null && !profile.email().isBlank()) {
      user.setEmail(profile.email());
    }
    if (profile.nickname() != null && !profile.nickname().isBlank()) {
      user.setNickname(profile.nickname());
    }
    if (profile.profileImageUrl() != null && !profile.profileImageUrl().isBlank()) {
      user.setProfileImageUrl(profile.profileImageUrl());
    }
    return user;
  }

  public record Result(
      User user,
      RefreshToken refreshToken
  ) {
  }
}
