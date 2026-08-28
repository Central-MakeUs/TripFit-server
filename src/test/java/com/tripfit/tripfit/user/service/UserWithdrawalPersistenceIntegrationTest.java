package com.tripfit.tripfit.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UserWithdrawalPersistenceIntegrationTest {

  @Autowired
  private UserWithdrawalPersistenceService persistenceService;

  @Autowired
  private UserRepository userRepository;

  @Test
  void finalizeWithdrawal_softDeletesUserAndScrubsPiiInRealDb() {
    User user =
        userRepository.save(
            new User(
                "withdraw-persistence-" + UUID.randomUUID(),
                SocialProvider.GOOGLE,
                "withdraw-persistence@example.com",
                "닉네임",
                "https://example.com/profile.png"));
    UUID userId = user.getId();

    persistenceService.finalizeWithdrawal(userId);

    User reloaded = userRepository.findById(userId).orElseThrow();
    assertThat(reloaded.getDeletedAt()).isNotNull();
    assertThat(reloaded.getEmail()).isNull();
    assertThat(reloaded.getNickname()).isNull();
    assertThat(reloaded.getProfileImageUrl()).isNull();
    assertThat(reloaded.isGoogleCalendarConnected()).isFalse();
  }

  @Test
  void finalizeWithdrawal_whenAlreadyWithdrawn_isIdempotentInRealDb() {
    User user =
        userRepository.save(
            new User(
                "withdraw-idempotent-" + UUID.randomUUID(),
                SocialProvider.GOOGLE,
                "withdraw-idempotent@example.com",
                "닉네임",
                null));
    UUID userId = user.getId();

    persistenceService.finalizeWithdrawal(userId);

    persistenceService.finalizeWithdrawal(userId);

    User reloaded = userRepository.findById(userId).orElseThrow();
    assertThat(reloaded.getDeletedAt()).isNotNull();
  }
}
