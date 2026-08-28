package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleLoginCredentialPersistenceService {

  private final GoogleLoginCredentialRepository googleLoginCredentialRepository;

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
