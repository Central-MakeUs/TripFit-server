package com.tripfit.tripfit.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SocialCredentialExecutorConfig {

  static final int EXCHANGE_THREADS = 4;

  // 작업 하나는 연결 3초·응답 5초 안에 끝나므로, 대기열이 가득 차도 마지막 작업이 100 ÷ 4 × 8초 = 200초 안에 시작된다.
  // Apple 인가 코드는 5분 동안만 쓸 수 있어 그보다 짧게 잡는다.
  static final int EXCHANGE_QUEUE_CAPACITY = 100;

  static final int SHUTDOWN_AWAIT_SECONDS = 30;

  // Apple·Google 로그인 인가 코드 교환만 맡는 전용 작업자다. 토큰 서버가 느려지면 이 스레드 4개만 묶이고,
  // 로그인 요청 처리 스레드는 교환을 기다리지 않는다. 서버가 내려갈 때는 남은 교환을 최대 30초 기다린다.
  @Bean
  ThreadPoolTaskExecutor socialCredentialExchangeExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("social-credential-");
    executor.setCorePoolSize(EXCHANGE_THREADS);
    executor.setMaxPoolSize(EXCHANGE_THREADS);
    executor.setQueueCapacity(EXCHANGE_QUEUE_CAPACITY);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
    return executor;
  }
}
