package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.jwt.AccessTokenClaims;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.RedisTokenRevocationChecker;
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
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final SocialTokenVerifierRegistry verifierRegistry;

  private final AuthLoginPersistenceService authLoginPersistenceService;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokenService;

  private final UserSummaryService userSummaryService;

  private final UserLookupService userLookupService;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  private final RedisTokenRevocationChecker tokenRevocationChecker;

  public AuthService(
      SocialTokenVerifierRegistry verifierRegistry,
      AuthLoginPersistenceService authLoginPersistenceService,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      UserSummaryService userSummaryService,
      UserLookupService userLookupService,
      AppleCredentialService appleCredentialService,
      GoogleLoginCredentialService googleLoginCredentialService,
      RedisTokenRevocationChecker tokenRevocationChecker) {
    this.verifierRegistry = verifierRegistry;
    this.authLoginPersistenceService = authLoginPersistenceService;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.userSummaryService = userSummaryService;
    this.userLookupService = userLookupService;
    this.appleCredentialService = appleCredentialService;
    this.googleLoginCredentialService = googleLoginCredentialService;
    this.tokenRevocationChecker = tokenRevocationChecker;
  }

  // 소셜 토큰을 검증하고 사용자 세션용 토큰 묶음을 발급함 — 소셜 provider HTTP 호출(토큰 검증·authorizationCode
  // 교환)은 DB 쓰기 트랜잭션 밖에서 먼저 끝내, provider 장애·지연이 DB 커넥션 풀을 붙잡지 않게 함
  // (AuthLoginPersistenceService 참고)
  public LoginResponse login(
      SocialProvider provider,
      String token,
      String authorizationCode,
      String redirectUri) {
    // 1. APPLE/GOOGLE인데 authorizationCode가 없으면 즉시 거부 — 탈퇴 시 provider revoke 호출이 항상 no-op이
    // 되는 걸 막기 위한 강제(APPLE은 App Store Guideline 5.1.1(v) 심사 요건, GOOGLE은 #64 재발견 gap 대응)
    if ((provider == SocialProvider.APPLE || provider == SocialProvider.GOOGLE)
        && (authorizationCode == null || authorizationCode.isBlank())) {
      throw new TripFitException(
          provider == SocialProvider.APPLE
              ? AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED
              : AuthErrorCode.AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED);
    }

    // 2. 소셜 제공자별 검증기를 찾아 외부 토큰을 검증함
    SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
    OAuthProfile profile = verifier.verify(token);

    // 3. 소셜 프로필 기준으로 사용자를 조회·저장하고 refresh token을 발급함 (짧은 DB 트랜잭션)
    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);
    User user = result.user();

    // 4. APPLE/GOOGLE이면 탈퇴 시 revoke용 refresh token을 교환·저장(best-effort — token 교환 자체가 실패해도
    // 로그인 흐름은 계속 진행). Apple client_id는 방금 aud 검증에서 매칭된 값(Bundle ID/Services ID)을 재사용 —
    // 로그인 경로와 이후 revoke가 항상 같은 client_id를 쓰도록 보장. Google은 재로그인 시 refresh_token이 없을 수
    // 있어(최초 동의 때만 발급) credential 저장 자체가 스킵될 수 있음(정상 케이스)
    if (provider == SocialProvider.APPLE) {
      appleCredentialService.saveIfAuthorizationCodePresent(
          user,
          authorizationCode,
          profile.appleMatchedClientId());
    } else if (provider == SocialProvider.GOOGLE) {
      googleLoginCredentialService.saveIfAuthorizationCodePresent(
          user,
          authorizationCode,
          redirectUri);
    }

    // 5. 액세스 토큰 발급 — user.hasPreSchedule은 toSummary()가 일정 EXISTS로 파생 (user 컬럼 아님)
    String accessToken = jwtService.createAccessToken(user.getId());
    return new LoginResponse(
        accessToken,
        result.refreshToken().getToken(),
        jwtService.getAccessExpirationSeconds(),
        userSummaryService.toSummary(user));
  }

  // RTR — 리프레시 토큰을 rotate(기존 토큰 폐기 + 같은 로그인 체인으로 새 토큰 발급)하고 새 액세스 토큰도 발급함.
  // 재사용(이미 폐기된 토큰 재제출)이면 RefreshTokenService가 AUTH_REFRESH_REUSE를 던짐
  @Transactional
  public RefreshResponse refresh(String refreshTokenValue) {
    RefreshToken rotated = refreshTokenService.rotate(refreshTokenValue);
    String accessToken = jwtService.createAccessToken(rotated.getUserId());
    return new RefreshResponse(
        accessToken, rotated.getToken(), jwtService.getAccessExpirationSeconds());
  }

  // 로그아웃 — 리프레시 토큰을 삭제하고, 클라이언트가 현재 access token을 같이 보냈으면 즉시 블랙리스트에 올림.
  // access token이 없거나 이미 만료·위조된 값이면 조용히 넘어감(로그아웃 자체는 항상 성공해야 함)
  @Transactional
  public void logout(String refreshTokenValue, String accessTokenValue) {
    refreshTokenService.delete(refreshTokenValue);
    if (accessTokenValue == null || accessTokenValue.isBlank()) {
      return;
    }
    try {
      AccessTokenClaims claims = jwtService.parseAccessToken(accessTokenValue);
      tokenRevocationChecker.revoke(claims.jti(), claims.expiresAt());
    } catch (TripFitException exception) {
      // 이미 만료·위조된 access token — 블랙리스트에 올릴 필요 없음
    }
  }

  // JWT userId로 현재 사용자 요약 조회 — hasPreSchedule은 일정 EXISTS 파생(일정 CRUD 후 me 재조회)
  @Transactional(readOnly = true)
  public UserSummaryResponse getCurrentUser(UUID userId) {
    return userSummaryService.toSummary(userLookupService.requireUser(userId));
  }
}
