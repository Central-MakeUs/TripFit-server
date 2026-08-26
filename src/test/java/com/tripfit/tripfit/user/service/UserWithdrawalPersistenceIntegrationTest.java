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

// A-2(트랜잭션 재구성)로 UserWithdrawalPersistenceService가 별도 빈으로 분리됐다 — Mockito 단위 테스트는
// self-invocation 없이 프록시를 제대로 타는지, 실제 커밋이 일어나는지 검증하지 못한다. 이 테스트는 실제
// MySQL(Testcontainers)로 finalizeWithdrawal()이 별도 빈 호출로도 트랜잭션이 걸려 정상 커밋되는지 확인한다 —
// 앱 스토어 심사 대응(2026-08-05)
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
    // 재호출해도 예외 없이 idempotent 종료돼야 함(이미 soft-delete된 계정)
    persistenceService.finalizeWithdrawal(userId);

    User reloaded = userRepository.findById(userId).orElseThrow();
    assertThat(reloaded.getDeletedAt()).isNotNull();
  }
}
