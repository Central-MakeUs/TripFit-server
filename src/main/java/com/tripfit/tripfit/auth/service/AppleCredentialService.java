package com.tripfit.tripfit.auth.service;

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

// Apple 탈퇴 시 revoke 호출에 쓸 refresh token을 로그인 시점에 저장·탈퇴 시점에 소비하는 credential 유스케이스 —
// Apple 토큰 엔드포인트 HTTP 호출은 여기서 트랜잭션 밖에서 수행하고, DB 조회·저장·삭제는 짧은 트랜잭션을 가진
// AppleCredentialPersistenceService에 위임한다(DB 커넥션이 provider 응답을 기다리며 열려 있지 않도록)
@Service
public class AppleCredentialService {

  private static final Logger log = LoggerFactory.getLogger(AppleCredentialService.class);

  private final AppleOAuthClient appleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final AppleCredentialPersistenceService persistenceService;

  public AppleCredentialService(
      AppleOAuthClient appleOAuthClient,
      SocialTokenCrypto tokenCrypto,
      AppleCredentialPersistenceService persistenceService) {
    this.appleOAuthClient = appleOAuthClient;
    this.tokenCrypto = tokenCrypto;
    this.persistenceService = persistenceService;
  }

  // authorizationCode를 refresh token으로 교환해 암호화 저장 — 재로그인마다 최신 code·client_id로 덮어씀. 실패해도
  // 로그인 자체는 계속 진행(best-effort). clientId는 로그인 aud 검증에서 실제로 매칭된 값(Bundle ID/Services ID)이라야
  // 함 — authorizationCode를 발급한 것과 다른 client_id로 교환하면 Apple이 거부함
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
          "Apple authorization code exchange failed — skipping credential save",
          exception);
    }
  }

  // 탈퇴 시 저장된 refresh token으로 Apple revoke 호출 후 credential row 삭제 — revoke 실패해도 삭제는 항상
  // 진행(best-effort). revoke는 로그인 시점에 저장해둔 client_id를 그대로 재사용(교환 때와 달라지면 Apple이 거부)
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
