package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import com.tripfit.tripfit.auth.repository.AppleCredentialRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleCredentialPersistenceServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private AppleCredentialRepository appleCredentialRepository;

  @Mock
  private UserLookupService userLookupService;

  @InjectMocks
  private AppleCredentialPersistenceService persistenceService;

  @Test
  void save_whenNewUser_createsCredential() {
    User user = user();
    when(userLookupService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(user));
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    assertThat(
        persistenceService.saveForActiveUser(USER_ID, "encrypted-refresh", "com.tripfit.app"))
        .isTrue();

    org.mockito.Mockito.verify(appleCredentialRepository)
        .save(
            ArgumentMatchers.argThat(
                credential -> "encrypted-refresh".equals(credential.getRefreshTokenCiphertext())
                    && "com.tripfit.app".equals(credential.getAppleClientId())));
  }

  @Test
  void save_whenExistingCredential_overwritesRefreshTokenAndClientId() {
    User user = user();
    when(userLookupService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(user));
    AppleCredential existing = AppleCredential.create(user, "old-ciphertext", "com.tripfit.app");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

    assertThat(
        persistenceService.saveForActiveUser(USER_ID, "new-ciphertext", "com.tripfit.service"))
        .isTrue();

    org.mockito.Mockito.verify(appleCredentialRepository).save(existing);
    assertThat(existing.getRefreshTokenCiphertext()).isEqualTo("new-ciphertext");
    assertThat(existing.getAppleClientId()).isEqualTo("com.tripfit.service");
  }

  @Test
  void findByUserId_delegatesToRepository() {
    User user = user();
    AppleCredential credential = AppleCredential.create(user, "ciphertext", "com.tripfit.app");
    when(appleCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(credential));

    Optional<AppleCredential> result = persistenceService.findByUserId(USER_ID);

    assertThat(result).contains(credential);
  }

  @Test
  void deleteByUserId_delegatesToRepository() {
    persistenceService.deleteByUserId(USER_ID);

    org.mockito.Mockito.verify(appleCredentialRepository).deleteByUser_Id(USER_ID);
  }

  // 탈퇴한(또는 없는) 사용자에게는 저장하지 않고 false를 돌려준다.
  @Test
  void saveForActiveUser_whenUserWithdrawn_skipsSaveAndReturnsFalse() {
    when(userLookupService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.empty());

    assertThat(
        persistenceService.saveForActiveUser(USER_ID, "encrypted-refresh", "com.tripfit.app"))
        .isFalse();

    org.mockito.Mockito.verify(appleCredentialRepository, org.mockito.Mockito.never())
        .save(ArgumentMatchers.any());
  }

  private static User user() {
    User user =
        new User(
            "apple-sub",
            SocialProvider.APPLE,
            "user@example.com",
            "닉네임",
            null);
    user.setId(USER_ID);
    return user;
  }
}
