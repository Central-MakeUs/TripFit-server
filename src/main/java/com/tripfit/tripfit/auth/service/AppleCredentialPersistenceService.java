package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleCredentialPersistenceService {

  private final AppleCredentialRepository appleCredentialRepository;

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
