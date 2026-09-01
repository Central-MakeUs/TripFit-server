package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.oauth.AppleOAuthClient;
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
public class AppleCredentialService {

  private static final Logger log = LoggerFactory.getLogger(AppleCredentialService.class);

  private final AppleOAuthClient appleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final AppleCredentialPersistenceService persistenceService;

  // Apple 로그인 시 함께 전달된 Authorization Code를 이용해 Refresh Token을 발급받고,
  // 이를 암호화하여 DB에 안전하게 저장합니다.
  public void saveIfAuthorizationCodePresent(User user, String authorizationCode, String clientId) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    try {
      String refreshToken =
          appleOAuthClient.exchangeAuthorizationCodeForRefreshToken(authorizationCode, clientId);
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      persistenceService.save(user, ciphertext, clientId);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext
              .of(SocialProvider.APPLE, SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(user.getId()),
          "Apple authorization code exchange failed. skipping credential save",
          exception);
    }
  }

  // 회원의 Apple 캘린더 등 연동 해제 시, DB에 저장된 암호화된 Refresh Token을 복호화하여
  // Apple 측 서버에 토큰 폐기 요청을 보낸 뒤 DB에서 자격 증명을 삭제합니다.
  public void revokeAndDeleteIfPresent(UUID userId) {
    persistenceService
        .findByUserId(userId)
        .ifPresent(
            (AppleCredential credential) -> {
              try {
                String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
                appleOAuthClient.revokeRefreshToken(refreshToken, credential.getAppleClientId());
              } catch (Exception exception) {
                SocialIntegrationLog.warn(
                    log,
                    SocialLogContext.of(
                        SocialProvider.APPLE,
                        SocialIntegrationAction.LOGIN_CREDENTIAL_REVOKE)
                        .withUserId(userId),
                    "Apple credential revoke failed",
                    exception);
              }
            });
    persistenceService.deleteByUserId(userId);
  }
}
