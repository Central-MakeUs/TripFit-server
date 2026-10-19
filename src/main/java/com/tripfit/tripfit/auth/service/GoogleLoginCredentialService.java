package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleLoginCredentialService {

  private static final Logger log = LoggerFactory.getLogger(GoogleLoginCredentialService.class);

  private final GoogleOAuthClient googleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final GoogleLoginCredentialPersistenceService persistenceService;

  // Google 로그인 인가 코드를 refresh token으로 바꿔 암호화해 저장한다. 로그인 응답 뒤 백그라운드에서 실행된다.
  // 교환이 끝나기 전에 사용자가 탈퇴했으면 저장하지 않고, 받은 토큰을 바로 폐기해 Google 쪽 연결도 끊는다.
  public void saveIfAuthorizationCodePresent(
      UUID userId,
      String authorizationCode,
      String redirectUri) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    String refreshToken;
    boolean saved;
    try {
      refreshToken =
          googleOAuthClient
              .exchangeAuthorizationCodeForRefreshToken(authorizationCode, redirectUri);
      if (refreshToken == null || refreshToken.isBlank()) {

        return;
      }
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      saved = persistenceService.saveForActiveUser(userId, ciphertext);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(
              SocialProvider.GOOGLE,
              SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(userId),
          "Google authorization code exchange failed. skipping credential save",
          exception);
      return;
    }
    if (!saved) {
      revokeIssuedTokenOfWithdrawnUser(userId, refreshToken);
    }
  }

  private void revokeIssuedTokenOfWithdrawnUser(UUID userId, String refreshToken) {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.LOGIN_CREDENTIAL_REVOKE)
            .withUserId(userId);
    SocialIntegrationLog.info(
        log,
        context,
        "User withdrew before credential exchange finished. revoking issued token");
    try {
      googleOAuthClient.revokeRefreshToken(refreshToken);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(log, context, "Google login credential revoke failed", exception);
    }
  }

  public void revokeAndDeleteIfPresent(UUID userId) {
    persistenceService
        .findByUserId(userId)
        .ifPresent(
            (GoogleLoginCredential credential) -> {
              try {
                String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
                googleOAuthClient.revokeRefreshToken(refreshToken);
              } catch (Exception exception) {
                SocialIntegrationLog.warn(
                    log,
                    SocialLogContext.of(
                        SocialProvider.GOOGLE,
                        SocialIntegrationAction.LOGIN_CREDENTIAL_REVOKE)
                        .withUserId(userId),
                    "Google login credential revoke failed",
                    exception);
              }
            });
    persistenceService.deleteByUserId(userId);
  }
}
