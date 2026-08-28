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

    SocialTokenVerifier verifier = verifierRegistry.getVerifier(provider);
    OAuthProfile profile = verifier.verify(token);

    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);
    User user = result.user();

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

    String accessToken = jwtService.createAccessToken(user.getId());
    LoginResponse response =
        new LoginResponse(
            accessToken,
            jwtService.getAccessExpirationSeconds(),
            userSummaryService.toSummary(user));
    return new LoginResult(response, result.refreshToken().token());
  }

  public RefreshResult refresh(String refreshTokenValue) {
    IssuedRefreshToken rotated = refreshTokenService.rotate(refreshTokenValue);
    String accessToken = jwtService.createAccessToken(rotated.userId());
    RefreshResponse response =
        new RefreshResponse(accessToken, jwtService.getAccessExpirationSeconds());
    return new RefreshResult(response, rotated.token());
  }

  public void logout(String refreshTokenValue) {
    if (refreshTokenValue != null) {
      refreshTokenService.delete(refreshTokenValue);
    }
  }

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
