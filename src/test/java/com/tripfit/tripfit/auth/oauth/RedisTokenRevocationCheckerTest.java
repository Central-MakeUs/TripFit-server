package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// 실제 Redis 컨테이너로 블랙리스트 등록·조회·TTL 자연 소멸을 검증. Redis 자체가 불가능한 상황(연결 실패)의
// fail-open은 별도 컨테이너 없이 잘못된 포트로 검증한다
class RedisTokenRevocationCheckerTest {

  private static GenericContainer<?> redisContainer;

  private static LettuceConnectionFactory connectionFactory;

  private static RedisTokenRevocationChecker checker;

  @BeforeAll
  static void startContainer() {
    redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    redisContainer.start();

    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(
            redisContainer.getHost(), redisContainer.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    checker = new RedisTokenRevocationChecker(redisTemplate);
  }

  @AfterAll
  static void stopContainer() {
    connectionFactory.destroy();
    redisContainer.stop();
  }

  @Test
  void isRevoked_neverBlacklisted_returnsFalse() {
    assertThat(checker.isRevoked("jti-never-seen")).isFalse();
  }

  @Test
  void revoke_thenIsRevoked_returnsTrue() {
    checker.revoke("jti-revoked", Instant.now().plusSeconds(60));

    assertThat(checker.isRevoked("jti-revoked")).isTrue();
  }

  @Test
  void revoke_alreadyExpiredToken_skipsRegistration() {
    checker.revoke("jti-already-expired", Instant.now().minusSeconds(1));

    assertThat(checker.isRevoked("jti-already-expired")).isFalse();
  }

  @Test
  void revoke_setsTtlMatchingRemainingLifetime() {
    checker.revoke("jti-ttl-check", Instant.now().plus(Duration.ofSeconds(120)));

    StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    Long ttl = redisTemplate.getExpire("auth:bl:jti-ttl-check");

    assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(120);
  }

  // Redis 접속 자체가 불가능한 상황(잘못된 포트) — 조회·등록 둘 다 예외를 던지지 않고 fail-open해야 함
  @Test
  void unreachableRedis_isRevoked_failsOpenInsteadOfThrowing() {
    LettuceConnectionFactory unreachableFactory =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
    unreachableFactory.afterPropertiesSet();
    StringRedisTemplate unreachableTemplate = new StringRedisTemplate(unreachableFactory);
    unreachableTemplate.afterPropertiesSet();
    RedisTokenRevocationChecker uncheckedChecker =
        new RedisTokenRevocationChecker(unreachableTemplate);

    boolean revoked = uncheckedChecker.isRevoked("jti-whatever");
    uncheckedChecker.revoke("jti-whatever", Instant.now().plusSeconds(60));

    assertThat(revoked).isFalse();
    unreachableFactory.destroy();
  }
}
