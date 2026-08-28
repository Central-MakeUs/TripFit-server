package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
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

  public void saveIfAuthorizationCodePresent(
      User user,
      String authorizationCode,
      String redirectUri) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    try {
      String refreshToken =
          googleOAuthClient
              .exchangeAuthorizationCodeForRefreshToken(authorizationCode, redirectUri);
      if (refreshToken == null || refreshToken.isBlank()) {

        return;
      }
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      persistenceService.save(user, ciphertext);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(
              SocialProvider.GOOGLE,
              SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(user.getId()),
          "Google authorization code exchange failed. skipping credential save",
          exception);
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
