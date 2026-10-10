package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.oauth.AppleOAuthClient;
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
public class AppleCredentialService {

  private static final Logger log = LoggerFactory.getLogger(AppleCredentialService.class);

  private final AppleOAuthClient appleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final AppleCredentialPersistenceService persistenceService;

  // Apple 로그인 시 함께 전달된 Authorization Code를 이용해 Refresh Token을 발급받고,
  // 이를 암호화하여 DB에 안전하게 저장합니다. 로그인 응답 뒤 백그라운드에서 실행됩니다.
  // 교환이 끝나기 전에 사용자가 탈퇴했으면 저장하지 않고, 받은 토큰을 바로 폐기해 Apple 쪽 연결도 끊습니다.
  public void saveIfAuthorizationCodePresent(
      UUID userId,
      String authorizationCode,
      String clientId) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    String refreshToken;
    boolean saved;
    try {
      refreshToken =
          appleOAuthClient.exchangeAuthorizationCodeForRefreshToken(authorizationCode, clientId);
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      saved = persistenceService.saveForActiveUser(userId, ciphertext, clientId);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext
              .of(SocialProvider.APPLE, SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(userId),
          "Apple authorization code exchange failed. skipping credential save",
          exception);
      return;
    }
    if (!saved) {
      revokeIssuedTokenOfWithdrawnUser(userId, refreshToken, clientId);
    }
  }

  private void revokeIssuedTokenOfWithdrawnUser(
      UUID userId,
      String refreshToken,
      String clientId) {
    SocialLogContext context =
        SocialLogContext.of(SocialProvider.APPLE, SocialIntegrationAction.LOGIN_CREDENTIAL_REVOKE)
            .withUserId(userId);
    SocialIntegrationLog.info(
        log,
        context,
        "User withdrew before credential exchange finished. revoking issued token");
    try {
      appleOAuthClient.revokeRefreshToken(refreshToken, clientId);
    } catch (Exception exception) {
      SocialIntegrationLog.warn(log, context, "Apple credential revoke failed", exception);
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
