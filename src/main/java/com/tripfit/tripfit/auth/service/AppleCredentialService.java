package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.oauth.AppleOAuthClient;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarTokenCrypto;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Apple 탈퇴 시 revoke 호출에 쓸 refresh token을 로그인 시점에 저장·탈퇴 시점에 소비하는 credential 유스케이스
@Service
public class AppleCredentialService {

  private static final Logger log = LoggerFactory.getLogger(AppleCredentialService.class);

  private final AppleCredentialRepository appleCredentialRepository;

  private final AppleOAuthClient appleOAuthClient;

  private final GoogleCalendarTokenCrypto tokenCrypto;

  public AppleCredentialService(
      AppleCredentialRepository appleCredentialRepository,
      AppleOAuthClient appleOAuthClient,
      GoogleCalendarTokenCrypto tokenCrypto) {
    this.appleCredentialRepository = appleCredentialRepository;
    this.appleOAuthClient = appleOAuthClient;
    this.tokenCrypto = tokenCrypto;
  }

  // authorizationCode를 refresh token으로 교환해 암호화 저장 — 재로그인마다 최신 code로 덮어씀. 실패해도 로그인 자체는 계속
  // 진행(best-effort)
  @Transactional
  public void saveIfAuthorizationCodePresent(User user, String authorizationCode) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    try {
      String refreshToken =
          appleOAuthClient.exchangeAuthorizationCodeForRefreshToken(authorizationCode);
      String ciphertext = tokenCrypto.encrypt(refreshToken);
      AppleCredential credential =
          appleCredentialRepository
              .findByUser_Id(user.getId())
              .map(
                  existing -> {
                    existing.updateRefreshToken(ciphertext);
                    return existing;
                  })
              .orElseGet(() -> AppleCredential.create(user, ciphertext));
      appleCredentialRepository.save(credential);
    } catch (Exception exception) {
      log.warn("Apple authorization code exchange failed — skipping credential save", exception);
    }
  }

  // 탈퇴 시 저장된 refresh token으로 Apple revoke 호출 후 credential row 삭제 — revoke 실패해도 삭제는 항상
  // 진행(best-effort)
  @Transactional
  public void revokeAndDeleteIfPresent(UUID userId) {
    appleCredentialRepository
        .findByUser_Id(userId)
        .ifPresent(
            (AppleCredential credential) -> {
              try {
                String refreshToken = tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
                appleOAuthClient.revokeRefreshToken(refreshToken);
              } catch (Exception exception) {
                log.warn("Apple credential revoke failed", exception);
              }
            });
    appleCredentialRepository.deleteByUser_Id(userId);
  }
}
