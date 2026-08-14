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

// Google 탈퇴 시 로그인 동의 자체를 revoke하기 위해, 로그인 시점에 authorization code로 교환한 refresh token을
// 저장·탈퇴 시점에 소비하는 credential 유스케이스(Apple 패턴과 동일 구조) — Google 토큰 엔드포인트 HTTP 호출은 여기서
// 트랜잭션 밖에서 수행하고, DB 조회·저장·삭제는 짧은 트랜잭션을 가진 GoogleLoginCredentialPersistenceService에 위임한다
@Service
@RequiredArgsConstructor
public class GoogleLoginCredentialService {

  private static final Logger log = LoggerFactory.getLogger(GoogleLoginCredentialService.class);

  private final GoogleOAuthClient googleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final GoogleLoginCredentialPersistenceService persistenceService;

  // authorizationCode를 refresh token으로 교환해 암호화 저장 — refresh_token이 없는 응답(재로그인 등 정상 케이스)은
  // 조용히 스킵하고 기존 credential을 그대로 둠. 교환 자체가 실패해도 로그인은 계속 진행(best-effort). redirectUri는
  // 네이티브 앱 로그인이면 null(빈 문자열로 처리), 브라우저 로그인이면 실제 리다이렉트에 쓴 URL
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
        // Google이 최초 동의 때만 refresh_token을 내려주므로 재로그인에서는 정상적으로 없을 수 있음
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
          "Google authorization code exchange failed — skipping credential save",
          exception);
    }
  }

  // 탈퇴 시 저장된 refresh token으로 Google revoke 호출 후 credential row 삭제 — revoke 실패해도 삭제는 항상
  // 진행(best-effort)
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
