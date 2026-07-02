package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifierRegistry;
import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserSummaryMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final SocialTokenVerifierRegistry verifierRegistry;

  private final UserRepository userRepository;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokenService;

  public AuthService(
      SocialTokenVerifierRegistry verifierRegistry,
      UserRepository userRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService) {
    this.verifierRegistry = verifierRegistry;
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
  }

  // 소셜 토큰을 검증하고 사용자 세션용 토큰 묶음을 발급함
  @Transactional
  public LoginResponse login(SocialProvider provider, String token) {
    // 1. 소셜 제공자별 검증기를 찾아 외부 토큰을 검증함
    SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
    OAuthProfile profile = verifier.verify(token);

    // 2. 소셜 프로필 기준으로 사용자를 조회하거나 신규 저장함
    User user = upsertUser(profile);

    // 3. 로그인 세션에 필요한 액세스 토큰과 리프레시 토큰을 발급함
    String accessToken = jwtService.createAccessToken(user.getId());
    RefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new LoginResponse(
        accessToken,
        refreshToken.getToken(),
        jwtService.getAccessExpirationSeconds(),
        UserSummaryMapper.toSummary(user));
  }

  // 리프레시 토큰으로 새로운 액세스 토큰을 재발급함
  @Transactional
  public RefreshResponse refresh(String refreshTokenValue) {
    try {
      // 1. 리프레시 토큰의 존재 여부와 만료 여부를 검증함
      RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);

      // 2. 검증된 사용자 ID로 새 액세스 토큰을 생성함
      String accessToken = jwtService.createAccessToken(refreshToken.getUserId());
      return new RefreshResponse(accessToken, jwtService.getAccessExpirationSeconds());
    } catch (TripFitException exception) {
      if (exception.getErrorCode() == AuthErrorCode.AUTH_INVALID_REFRESH) {
        // 만료 refresh로 재시도 시 row를 남겨두면 동일 토큰으로 반복 호출 가능 — 정리 후 401
        refreshTokenService.deleteExpired(refreshTokenValue);
      }
      throw exception;
    }
  }

  // 로그아웃 요청에 해당하는 리프레시 토큰을 삭제함
  @Transactional
  public void logout(String refreshTokenValue) {
    refreshTokenService.delete(refreshTokenValue);
  }

  // JWT에 담긴 userId로 현재 로그인 사용자 프로필을 조회함
  @Transactional(readOnly = true)
  public UserSummaryResponse getCurrentUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
    return UserSummaryMapper.toSummary(user);
  }

  // 소셜 계정 기준으로 사용자를 조회하고 없으면 새로 생성함
  private User upsertUser(OAuthProfile profile) {
    return userRepository
        .findByProviderAndSocialId(profile.provider(), profile.providerUserId())
        .map(existing -> updateFromProfile(existing, profile))
        .orElseGet(() -> userRepository.save(createUserFromProfile(profile)));
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

  // 재로그인 시 소셜 프로필에서 전달된 필드만 갱신 — first/last는 PATCH profile 전용 (BR-USER-001)
  // profileImageUrl: A안 provider URL passthrough (006). B안 S3 미러는 wave 4
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
}
