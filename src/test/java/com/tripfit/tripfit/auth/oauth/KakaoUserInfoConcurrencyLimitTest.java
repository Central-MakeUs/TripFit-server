package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.config.ResilienceConfig;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;

// 카카오 user/me 동시 호출 상한이 실제 스프링 프록시로 걸리는지, 일부러 느리게 응답하는 로컬 HTTP 서버로 확인한다.
@SpringJUnitConfig(KakaoUserInfoConcurrencyLimitTest.TestBeans.class)
class KakaoUserInfoConcurrencyLimitTest {

  private static final CountDownLatch release = new CountDownLatch(1);

  private static final AtomicInteger received = new AtomicInteger();

  private static HttpServer server;

  @Autowired
  private KakaoTokenVerifier kakaoTokenVerifier;

  @BeforeAll
  static void startSlowKakao() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext(
        "/v2/user/me",
        exchange -> {
          received.incrementAndGet();
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          byte[] body = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
  }

  @AfterAll
  static void stopSlowKakao() {
    release.countDown();
    server.stop(0);
  }

  // 20개가 카카오 응답을 기다리는 동안 21번째 로그인은 기다리지 않고 바로 503으로 끝난다.
  // 기다리던 호출들은 카카오가 응답하면 정상으로 끝나고, 그 뒤 새 호출도 다시 받는다.
  @Test
  void verify_whenLimitReached_rejectsImmediatelyWithProviderUnavailable() throws Exception {
    ExecutorService callers = Executors.newFixedThreadPool(KakaoUserInfoClient.CONCURRENCY_LIMIT);
    try {
      List<Future<OAuthProfile>> inFlight = new ArrayList<>();
      for (int i = 0; i < KakaoUserInfoClient.CONCURRENCY_LIMIT; i++) {
        inFlight.add(callers.submit(() -> kakaoTokenVerifier.verify("token")));
      }
      for (int i = 0; i < 100 && received.get() < KakaoUserInfoClient.CONCURRENCY_LIMIT; i++) {
        Thread.sleep(50);
      }
      assertThat(received.get()).isEqualTo(KakaoUserInfoClient.CONCURRENCY_LIMIT);

      long startedAt = System.nanoTime();
      assertThatThrownBy(() -> kakaoTokenVerifier.verify("token"))
          .isInstanceOf(TripFitException.class)
          .extracting(exception -> ((TripFitException) exception).getErrorCode())
          .isEqualTo(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
      assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(1));
      assertThat(received.get()).isEqualTo(KakaoUserInfoClient.CONCURRENCY_LIMIT);

      release.countDown();
      for (Future<OAuthProfile> call : inFlight) {
        assertThat(call.get(10, TimeUnit.SECONDS).providerUserId()).isEqualTo("1");
      }
      assertThat(kakaoTokenVerifier.verify("token").providerUserId()).isEqualTo("1");
    } finally {
      callers.shutdownNow();
    }
  }

  @Configuration
  @Import({ResilienceConfig.class, KakaoUserInfoClient.class, KakaoTokenVerifier.class})
  static class TestBeans {

    @Bean
    RestClient restClient() {
      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(Duration.ofSeconds(3));
      requestFactory.setReadTimeout(Duration.ofSeconds(15));
      return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    OAuthProperties oAuthProperties() {
      OAuthProperties properties = new OAuthProperties();
      properties.setKakaoUserMeUrl(
          "http://localhost:" + server.getAddress().getPort() + "/v2/user/me");
      return properties;
    }
  }
}
