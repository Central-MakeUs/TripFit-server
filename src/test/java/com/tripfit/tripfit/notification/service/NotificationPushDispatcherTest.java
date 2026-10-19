package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationPushDispatcherTest {

  private final FcmService fcmService = mock(FcmService.class);

  private final CountDownLatch release = new CountDownLatch(1);

  private final ThreadPoolTaskExecutor executor = singleThreadWithoutQueue();

  @AfterEach
  void tearDown() {
    release.countDown();
    executor.shutdown();
  }

  // 작업자와 대기열이 모두 차 있으면 새 발송은 거절되고, 거절은 호출한 쪽으로 예외가 되어 나가지 않는다.
  @Test
  void submit_whenExecutorSaturated_skipsPushWithoutThrowing() throws Exception {
    doAnswer(invocation -> release.await(10, TimeUnit.SECONDS))
        .when(fcmService)
        .sendMulticast(any(), any(), any(), any(), any());
    NotificationPushDispatcher dispatcher = new NotificationPushDispatcher(executor, fcmService);

    dispatcher.submit(push());
    verify(fcmService, timeout(5_000)).sendMulticast(any(), any(), any(), any(), any());

    assertThatCode(() -> dispatcher.submit(push())).doesNotThrowAnyException();

    release.countDown();
    Thread.sleep(200);
    verify(fcmService, times(1)).sendMulticast(any(), any(), any(), any(), any());
  }

  private static NotificationPush push() {
    return new NotificationPush(
        NotificationType.TRIP_INFO_CHANGED,
        Map.of("token-1", UUID.randomUUID()),
        "제목",
        "본문",
        LandingType.TRAVEL_ROOM_DETAIL,
        UUID.randomUUID());
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
