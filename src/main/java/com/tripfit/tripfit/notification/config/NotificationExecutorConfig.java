package com.tripfit.tripfit.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NotificationExecutorConfig {

  static final int PUSH_THREADS = 4;

  static final int PUSH_QUEUE_CAPACITY = 200;

  static final int SHUTDOWN_AWAIT_SECONDS = 30;

  // FCM 발송만 맡는 전용 작업자다. FCM이 멈추면 이 스레드 4개만 묶이고, 대기열 200개가 차면 새 발송은 거절된다.
  // 요청 처리 스레드나 DB 커넥션과는 자원을 나누지 않으므로 FCM 장애가 다른 API로 번지지 않는다.
  // 서버가 내려갈 때는 남은 발송을 최대 30초 기다린다.
  @Bean
  ThreadPoolTaskExecutor notificationPushExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("notification-push-");
    executor.setCorePoolSize(PUSH_THREADS);
    executor.setMaxPoolSize(PUSH_THREADS);
    executor.setQueueCapacity(PUSH_QUEUE_CAPACITY);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
    return executor;
  }
}
