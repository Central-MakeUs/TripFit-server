package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleCredentialPersistenceService {

  private final AppleCredentialRepository appleCredentialRepository;

  private final UserLookupService userLookupService;

  // 유저 ID로 Apple 연동 정보를 조회합니다.
  @Transactional(readOnly = true)
  public Optional<AppleCredential> findByUserId(UUID userId) {
    return appleCredentialRepository.findByUser_Id(userId);
  }

  // Apple 연동 정보(암호화된 Refresh Token)를 저장하거나 갱신합니다. 탈퇴하지 않은 사용자에게만 저장하고,
  // 사용자 행을 잠근 채 확인해 같은 사용자의 탈퇴와 엇갈리지 않게 합니다. 저장하지 않았으면 false를 돌려줍니다.
  @Transactional
  public boolean saveForActiveUser(UUID userId, String refreshTokenCiphertext, String clientId) {
    Optional<User> activeUser = userLookupService.findActiveUserForUpdate(userId);
    if (activeUser.isEmpty()) {
      return false;
    }
    User user = activeUser.get();
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
    return true;
  }

  @Transactional
  public void deleteByUserId(UUID userId) {
    appleCredentialRepository.deleteByUser_Id(userId);
  }
}
