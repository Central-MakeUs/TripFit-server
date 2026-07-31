package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.domain.User;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Google 탈퇴 시 로그인 동의 자체를 revoke하기 위해, 로그인 시점에 authorization code로 교환한 refresh token을
// 저장·탈퇴 시점에 소비하는 credential 유스케이스(Apple 패턴과 동일 구조)
@Service
public class GoogleLoginCredentialService {

  private static final Logger log = LoggerFactory.getLogger(GoogleLoginCredentialService.class);

  private final GoogleLoginCredentialRepository googleLoginCredentialRepository;

  private final GoogleOAuthClient googleOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  public GoogleLoginCredentialService(
      GoogleLoginCredentialRepository googleLoginCredentialRepository,
      GoogleOAuthClient googleOAuthClient,
      SocialTokenCrypto tokenCrypto) {
    this.googleLoginCredentialRepository = googleLoginCredentialRepository;
    this.googleOAuthClient = googleOAuthClient;
    this.tokenCrypto = tokenCrypto;
  }

  // authorizationCode를 refresh token으로 교환해 암호화 저장 — refresh_token이 없는 응답(재로그인 등 정상 케이스)은
  // 조용히 스킵하고 기존 credential을 그대로 둠. 교환 자체가 실패해도 로그인은 계속 진행(best-effort)
  @Transactional
  public void saveIfAuthorizationCodePresent(User user, String authorizationCode) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    try {
      String refreshToken =
          googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(authorizationCode);
      if (refreshToken == null || refreshToken.isBlank()) {
        // Google이 최초 동의 때만 refresh_token을 내려주므로 재로그인에서는 정상적으로 없을 수 있음
        return;
      }
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      GoogleLoginCredential credential =
          googleLoginCredentialRepository
              .findByUser_Id(user.getId())
              .map(
                  existing -> {
                    existing.updateRefreshToken(ciphertext);
                    return existing;
                  })
              .orElseGet(() -> GoogleLoginCredential.create(user, ciphertext));
      googleLoginCredentialRepository.save(credential);
    } catch (Exception exception) {
      log.warn("Google authorization code exchange failed — skipping credential save", exception);
    }
  }

  // 탈퇴 시 저장된 refresh token으로 Google revoke 호출 후 credential row 삭제 — revoke 실패해도 삭제는 항상
  // 진행(best-effort)
  @Transactional
  public void revokeAndDeleteIfPresent(UUID userId) {
    googleLoginCredentialRepository
        .findByUser_Id(userId)
        .ifPresent(
            (GoogleLoginCredential credential) -> {
              try {
                String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
                googleOAuthClient.revokeRefreshToken(refreshToken);
              } catch (Exception exception) {
                log.warn("Google login credential revoke failed", exception);
              }
            });
    googleLoginCredentialRepository.deleteByUser_Id(userId);
  }
}
