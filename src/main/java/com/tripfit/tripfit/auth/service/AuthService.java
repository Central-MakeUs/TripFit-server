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
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final SocialTokenVerifierRegistry verifierRegistry;

  private final UserRepository userRepository;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokenService;

  private final UserSummaryService userSummaryService;

  private final UserLookupService userLookupService;

  private final AppleCredentialService appleCredentialService;

  public AuthService(
      SocialTokenVerifierRegistry verifierRegistry,
      UserRepository userRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      UserSummaryService userSummaryService,
      UserLookupService userLookupService,
      AppleCredentialService appleCredentialService) {
    this.verifierRegistry = verifierRegistry;
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.userSummaryService = userSummaryService;
    this.userLookupService = userLookupService;
    this.appleCredentialService = appleCredentialService;
  }

  // 소셜 토큰을 검증하고 사용자 세션용 토큰 묶음을 발급함
  @Transactional
  public LoginResponse login(SocialProvider provider, String token, String authorizationCode) {
    // 1. APPLE인데 authorizationCode가 없으면 즉시 거부 — 탈퇴 시 Apple revoke 호출이 항상 no-op이 되는 걸 막기 위한
    // 강제(App Store Guideline 5.1.1(v) 심사 요건)
    if (provider == SocialProvider.APPLE
        && (authorizationCode == null || authorizationCode.isBlank())) {
      throw new TripFitException(AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED);
    }

    // 2. 소셜 제공자별 검증기를 찾아 외부 토큰을 검증함
    SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
    OAuthProfile profile = verifier.verify(token);

    // 3. 소셜 프로필 기준으로 사용자를 조회하거나 신규 저장함
    User user = upsertUser(profile);

    // 4. APPLE이면 탈퇴 시 revoke용 refresh token을 교환·저장(best-effort — token 교환 자체가 실패해도 로그인 흐름은 계속
    // 진행)
    if (provider == SocialProvider.APPLE) {
      appleCredentialService.saveIfAuthorizationCodePresent(user, authorizationCode);
    }

    // 5. 액세스·리프레시 발급 — user.hasPreSchedule은 toSummary()가 일정 EXISTS로 파생 (user 컬럼 아님)
    String accessToken = jwtService.createAccessToken(user.getId());
    RefreshToken refreshToken = refreshTokenService.create(user.getId());
    return new LoginResponse(
        accessToken,
        refreshToken.getToken(),
        jwtService.getAccessExpirationSeconds(),
        userSummaryService.toSummary(user));
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

  // JWT userId로 현재 사용자 요약 조회 — hasPreSchedule은 일정 EXISTS 파생(일정 CRUD 후 me 재조회)
  @Transactional(readOnly = true)
  public UserSummaryResponse getCurrentUser(UUID userId) {
    return userSummaryService.toSummary(userLookupService.requireUser(userId));
  }

  // 소셜 계정 기준으로 사용자를 조회하고 없으면 새로 생성함 — 탈퇴(soft-deleted) 계정은 부활시켜 재가입을 대체
  private User upsertUser(OAuthProfile profile) {
    Optional<User> existing =
        userRepository.findByProviderAndSocialId(profile.provider(), profile.providerUserId());
    return existing
        .map(user -> updateFromProfile(reviveIfWithdrawn(user), profile))
        .orElseGet(() -> userRepository.save(createUserFromProfile(profile)));
  }

  // 탈퇴 계정 재가입 정책(2026-07-27 확정): soft-deleted 계정이 같은 소셜 계정으로 재로그인하면 기존 row를 그대로 부활시킴 —
  // (provider, social_id) UNIQUE 제약 때문에 신규 row를 새로 만들 수 없어 기존 row 재사용. firstName/lastName·구글 캘린더
  // 연동은
  // 탈퇴 시 초기화된 채로 남아 재온보딩이 필요함(신규 가입과 동일한 경험)
  private User reviveIfWithdrawn(User user) {
    if (user.getDeletedAt() != null) {
      user.setDeletedAt(null);
      user.setAllFree(false);
    }
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
}
