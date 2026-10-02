package com.tripfit.tripfit.common.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.common.config.ResilienceConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {ResilienceConfig.class, VersionConflictRetryExecutor.class})
class VersionConflictRetryTest {

  @Autowired
  private VersionConflictRetryExecutor executor;

  @Test
  void run_whenConflictClears_retriesUntilSuccess() {
    AtomicInteger attempts = new AtomicInteger();

    executor.run(
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw versionConflict();
          }
        });

    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void run_whenConflictPersists_rethrowsOriginalExceptionAfterSixAttempts() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
        () -> executor.run(
            () -> {
              attempts.incrementAndGet();
              throw versionConflict();
            }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    // 처음 1번에 다시 시도 5번을 더해 6번까지만 실행하고, 감싸지 않은 원래 예외를 그대로 던진다.
    assertThat(attempts.get()).isEqualTo(6);
  }

  @Test
  void run_whenDeadlock_doesNotRetry() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
        () -> executor.run(
            () -> {
              attempts.incrementAndGet();
              throw new CannotAcquireLockException("Deadlock found when trying to get lock");
            }))
        .isInstanceOf(CannotAcquireLockException.class);

    assertThat(attempts.get()).isEqualTo(1);
  }

  private static ObjectOptimisticLockingFailureException versionConflict() {
    return new ObjectOptimisticLockingFailureException(Object.class, "id");
  }
}
