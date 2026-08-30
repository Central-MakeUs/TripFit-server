package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Apple credential의 DB 조회·저장·삭제만 담당하는 짧은 트랜잭션 — AppleCredentialService가 Apple 토큰 엔드포인트
// HTTP 호출(교환·revoke)을 이 트랜잭션 밖에서 먼저 끝내도록 분리한 것(AuthLoginPersistenceService와 동일 패턴).
// AppleCredentialService의 private 메서드로 두면 self-invocation 때문에 @Transactional 프록시가 안 걸려 별도 빈으로 둔다
@Service
public class AppleCredentialPersistenceService {

  private final AppleCredentialRepository appleCredentialRepository;

  public AppleCredentialPersistenceService(AppleCredentialRepository appleCredentialRepository) {
    this.appleCredentialRepository = appleCredentialRepository;
  }

  @Transactional(readOnly = true)
  public Optional<AppleCredential> findByUserId(UUID userId) {
    return appleCredentialRepository.findByUser_Id(userId);
  }

  @Transactional
  public void save(User user, String refreshTokenCiphertext, String clientId) {
    AppleCredential credential =
        appleCredentialRepository
            .findByUser_Id(user.getId())
            .map(
                existing -> {
                  existing.update(refreshTokenCiphertext, clientId);
                  return existing;
                })
            .orElseGet(() -> AppleCredential.create(user, refreshTokenCiphertext, clientId));
    appleCredentialRepository.save(credential);
  }

  @Transactional
  public void deleteByUserId(UUID userId) {
    appleCredentialRepository.deleteByUser_Id(userId);
  }
}
