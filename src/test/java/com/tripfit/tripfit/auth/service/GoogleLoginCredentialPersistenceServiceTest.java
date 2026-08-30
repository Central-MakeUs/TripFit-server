package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleLoginCredentialPersistenceServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  @Mock
  private GoogleLoginCredentialRepository googleLoginCredentialRepository;

  @InjectMocks
  private GoogleLoginCredentialPersistenceService persistenceService;

  @Test
  void save_whenNewUser_createsCredential() {
    User user = user();
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

    persistenceService.save(user, "encrypted-refresh");

    org.mockito.Mockito.verify(googleLoginCredentialRepository)
        .save(
            ArgumentMatchers.argThat(
                credential -> "encrypted-refresh".equals(credential.getRefreshTokenCiphertext())));
  }

  @Test
  void save_whenExistingCredential_overwritesRefreshToken() {
    User user = user();
    GoogleLoginCredential existing = GoogleLoginCredential.create(user, "old-ciphertext");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(existing));

    persistenceService.save(user, "new-ciphertext");

    org.mockito.Mockito.verify(googleLoginCredentialRepository).save(existing);
    assertThat(existing.getRefreshTokenCiphertext()).isEqualTo("new-ciphertext");
  }

  @Test
  void findByUserId_delegatesToRepository() {
    User user = user();
    GoogleLoginCredential credential = GoogleLoginCredential.create(user, "ciphertext");
    when(googleLoginCredentialRepository.findByUser_Id(USER_ID))
        .thenReturn(Optional.of(credential));

    Optional<GoogleLoginCredential> result = persistenceService.findByUserId(USER_ID);

    assertThat(result).contains(credential);
  }

  @Test
  void deleteByUserId_delegatesToRepository() {
    persistenceService.deleteByUserId(USER_ID);

    org.mockito.Mockito.verify(googleLoginCredentialRepository).deleteByUser_Id(USER_ID);
  }

  private static User user() {
    User user =
        new User(
            "google-sub",
            SocialProvider.GOOGLE,
            "user@example.com",
            "닉네임",
            null);
    user.setId(USER_ID);
    return user;
  }
}
