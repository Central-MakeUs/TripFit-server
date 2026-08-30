package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Google login credential의 DB 조회·저장·삭제만 담당하는 짧은 트랜잭션 — GoogleLoginCredentialService가 Google
// 토큰 엔드포인트 HTTP 호출(교환·revoke)을 이 트랜잭션 밖에서 먼저 끝내도록 분리한 것(AppleCredentialPersistenceService와
// 동일 패턴). self-invocation 때문에 별도 빈으로 둔다
@Service
public class GoogleLoginCredentialPersistenceService {

  private final GoogleLoginCredentialRepository googleLoginCredentialRepository;

  public GoogleLoginCredentialPersistenceService(
      GoogleLoginCredentialRepository googleLoginCredentialRepository) {
    this.googleLoginCredentialRepository = googleLoginCredentialRepository;
  }

  @Transactional(readOnly = true)
  public Optional<GoogleLoginCredential> findByUserId(UUID userId) {
    return googleLoginCredentialRepository.findByUser_Id(userId);
  }

  @Transactional
  public void save(User user, String refreshTokenCiphertext) {
    GoogleLoginCredential credential =
        googleLoginCredentialRepository
            .findByUser_Id(user.getId())
            .map(
                existing -> {
                  existing.updateRefreshToken(refreshTokenCiphertext);
                  return existing;
                })
            .orElseGet(() -> GoogleLoginCredential.create(user, refreshTokenCiphertext));
    googleLoginCredentialRepository.save(credential);
  }

  @Transactional
  public void deleteByUserId(UUID userId) {
    googleLoginCredentialRepository.deleteByUser_Id(userId);
  }
}
