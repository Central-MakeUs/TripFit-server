package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class LoginCredentialExchangeDispatcherTest {

  private final AppleCredentialService appleCredentialService = mock(AppleCredentialService.class);

  private final GoogleLoginCredentialService googleLoginCredentialService =
      mock(GoogleLoginCredentialService.class);

  private final CountDownLatch release = new CountDownLatch(1);

  private final ThreadPoolTaskExecutor executor = singleThreadWithoutQueue();

  private final LoginCredentialExchangeDispatcher dispatcher =
      new LoginCredentialExchangeDispatcher(
          executor, appleCredentialService, googleLoginCredentialService);

  @AfterEach
  void tearDown() {
    release.countDown();
    executor.shutdown();
  }

  // 작업자와 대기열이 모두 차 있으면 교환을 건너뛰고, 로그인 요청으로 예외가 나가지 않는다.
  @Test
  void submit_whenExecutorSaturated_skipsExchangeWithoutThrowing() throws Exception {
    doAnswer(invocation -> release.await(10, TimeUnit.SECONDS))
        .when(googleLoginCredentialService)
        .saveIfAuthorizationCodePresent(any(), any(), any());

    dispatcher.submitGoogle(UUID.randomUUID(), "code-1", null);
    verify(googleLoginCredentialService, timeout(5_000))
        .saveIfAuthorizationCodePresent(any(), any(), any());

    assertThatCode(() -> dispatcher.submitGoogle(UUID.randomUUID(), "code-2", null))
        .doesNotThrowAnyException();

    release.countDown();
    Thread.sleep(200);
    verify(googleLoginCredentialService, times(1))
        .saveIfAuthorizationCodePresent(any(), any(), any());
  }

  // 인가 코드가 없으면 작업을 만들지 않는다.
  @Test
  void submit_whenCodeBlank_submitsNothing() {
    dispatcher.submitApple(UUID.randomUUID(), " ", "com.tripfit.app");

    verifyNoInteractions(appleCredentialService);
  }

  private static ThreadPoolTaskExecutor singleThreadWithoutQueue() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.initialize();
    return executor;
  }
}
