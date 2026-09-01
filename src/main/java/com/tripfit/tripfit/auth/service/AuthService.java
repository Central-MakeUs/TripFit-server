package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifierRegistry;
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

  // 소셜 로그인 제공자(Apple/Google)와 토큰을 받아 인증을 처리하고,
  // 내부 User 엔티티 생성/조회 후 Access/Refresh Token을 발급합니다.
  public LoginResult login(
      SocialProvider provider,
      String token,
      String authorizationCode,
      String redirectUri) {

    if ((provider == SocialProvider.APPLE || provider == SocialProvider.GOOGLE)
        && (authorizationCode == null || authorizationCode.isBlank())) {
      throw new TripFitException(
          provider == SocialProvider.APPLE
              ? AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED
              : AuthErrorCode.AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED);
    }

    // 1. 소셜 제공자에 맞는 Token Verifier를 가져와 전달받은 ID Token을 검증합니다.
    SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
    OAuthProfile profile = verifier.verify(token);

    // 2. 인증된 프로필 정보를 바탕으로 신규 유저 가입 또는 기존 유저 조회를 수행합니다.
    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);
    User user = result.user();

    // 3. Apple, Google 로그인 시 제공된 인가 코드(Authorization Code)가 있다면
    // 백그라운드 작업(캘린더 연동 등)을 위해 Credential을 안전하게 저장합니다.
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

    // 4. 서비스 자체 AccessToken(JWT)을 발급하여 로그인 응답 객체를 반환합니다.
    // RefreshToken은 persist 단계에서 이미 처리되어 반환됩니다.
    String accessToken = jwtService.createAccessToken(user.getId());
    LoginResponse response =
        new LoginResponse(
            accessToken,
            jwtService.getAccessExpirationSeconds(),
            userSummaryService.toSummary(user));
    return new LoginResult(response, result.refreshToken().token());
  }

  // Refresh Token을 사용하여 새로운 Access Token과 교체된 새 Refresh Token을 발급합니다.
  public RefreshResult refresh(String refreshTokenValue) {
    IssuedRefreshToken rotated = refreshTokenService.rotate(refreshTokenValue);
    String accessToken = jwtService.createAccessToken(rotated.userId());
    RefreshResponse response =
        new RefreshResponse(accessToken, jwtService.getAccessExpirationSeconds());
    return new RefreshResult(response, rotated.token());
  }

  // 클라이언트가 전달한 Refresh Token을 폐기 처리하여 로그아웃합니다.
  public void logout(String refreshTokenValue) {
    if (refreshTokenValue != null) {
      refreshTokenService.delete(refreshTokenValue);
    }
  }

  // 현재 로그인한 사용자의 정보를 요약(UserSummaryResponse) 형태로 반환합니다.
  @Transactional(readOnly = true)
  public UserSummaryResponse getCurrentUser(UUID userId) {
    return userSummaryService.toSummary(userLookupService.requireUser(userId));
  }

  public record LoginResult(
      LoginResponse response,
      String refreshToken
  ) {
  }

  public record RefreshResult(
      RefreshResponse response,
      String refreshToken
  ) {
  }
}
