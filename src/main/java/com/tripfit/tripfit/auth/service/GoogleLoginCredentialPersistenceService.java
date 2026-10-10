package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleLoginCredentialPersistenceService {

  private final GoogleLoginCredentialRepository googleLoginCredentialRepository;

  private final UserLookupService userLookupService;

  @Transactional(readOnly = true)
  public Optional<GoogleLoginCredential> findByUserId(UUID userId) {
    return googleLoginCredentialRepository.findByUser_Id(userId);
  }

  // 탈퇴하지 않은 사용자에게만 저장하고, 사용자 행을 잠근 채 확인해 같은 사용자의 탈퇴와 엇갈리지 않게 한다.
  // 저장하지 않았으면 false를 돌려준다.
  @Transactional
  public boolean saveForActiveUser(UUID userId, String refreshTokenCiphertext) {
    Optional<User> activeUser = userLookupService.findActiveUserForUpdate(userId);
    if (activeUser.isEmpty()) {
      return false;
    }
    User user = activeUser.get();
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
    return true;
  }

  @Transactional
  public void deleteByUserId(UUID userId) {
    googleLoginCredentialRepository.deleteByUser_Id(userId);
  }
}
