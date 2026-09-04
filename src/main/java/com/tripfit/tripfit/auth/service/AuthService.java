package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
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
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final SocialTokenVerifierRegistry verifierRegistry;

  private final AuthLoginPersistenceService authLoginPersistenceService;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokenService;

  private final UserSummaryService userSummaryService;

  private final UserLookupService userLookupService;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

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
}
