package com.tripfit.tripfit.trip.membership.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// 실제 Redis 컨테이너로 hold 생성·TTL 기반 만료·명시적 해제·동시 요청 원자성을 검증한다. Redis 자체가 불가능한
// 상황(연결 실패)의 fail-open은 별도 컨테이너 없이 잘못된 포트로 검증한다(RedisTokenRevocationCheckerTest와 동일 패턴)
class TripJoinHoldServiceTest {

  private static GenericContainer<?> redisContainer;

  private static LettuceConnectionFactory connectionFactory;

  private static StringRedisTemplate redisTemplate;

  private static TripJoinHoldService holdService;

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

    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    holdService = new TripJoinHoldService(redisTemplate);
  }

  @AfterAll
  static void stopContainer() {
    connectionFactory.destroy();
    redisContainer.stop();
  }

  @BeforeEach
  void flushRedis() {
    redisTemplate.execute(
        (org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  @Test
  void tryHold_roomAvailable_succeeds() {
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    boolean held = holdService.tryHold(tripId, userId, 0, 6);

    assertThat(held).isTrue();
    assertThat(holdService.hasActiveHold(tripId, userId)).isTrue();
    assertThat(holdService.countActiveHolds(tripId)).isEqualTo(1);
  }

  @Test
  void tryHold_sameUserTwice_refreshesWithoutDoubleCounting() {
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    boolean first = holdService.tryHold(tripId, userId, 0, 1);
    boolean second = holdService.tryHold(tripId, userId, 0, 1);

    assertThat(first).isTrue();
    assertThat(second).isTrue();
    assertThat(holdService.countActiveHolds(tripId)).isEqualTo(1);
  }

  @Test
  void tryHold_capacityFull_returnsFalse() {
    UUID tripId = UUID.randomUUID();
    UUID firstUser = UUID.randomUUID();
    UUID secondUser = UUID.randomUUID();

    boolean first = holdService.tryHold(tripId, firstUser, 0, 1);
    boolean second = holdService.tryHold(tripId, secondUser, 0, 1);

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(holdService.hasActiveHold(tripId, secondUser)).isFalse();
    assertThat(holdService.countActiveHolds(tripId)).isEqualTo(1);
  }

  @Test
  void tryHold_confirmedMembersAlreadyFillCapacity_returnsFalse() {
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // 확정 멤버 수만으로 이미 정원(6) 도달 — hold 자리 없음
    boolean held = holdService.tryHold(tripId, userId, 6, 6);

    assertThat(held).isFalse();
  }

  @Test
  void release_removesHold_freeingCapacityForOthers() {
    UUID tripId = UUID.randomUUID();
    UUID firstUser = UUID.randomUUID();
    UUID secondUser = UUID.randomUUID();
    holdService.tryHold(tripId, firstUser, 0, 1);

    holdService.release(tripId, firstUser);

    assertThat(holdService.hasActiveHold(tripId, firstUser)).isFalse();
    assertThat(holdService.tryHold(tripId, secondUser, 0, 1)).isTrue();
  }

  @Test
  void release_nonExistentHold_isSafeNoOp() {
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    holdService.release(tripId, userId);

    assertThat(holdService.hasActiveHold(tripId, userId)).isFalse();
  }

  // 실제 TTL(10분)을 그대로 기다리는 대신, 이미 만료된 것으로 보이는 score를 직접 심어 만료 판정 로직만 검증
  @Test
  void expiredHold_isTreatedAsInactive() {
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String key = "trip:" + tripId + ":holds";
    redisTemplate
        .opsForZSet()
        .add(key, userId.toString(), Instant.now().minusSeconds(60).toEpochMilli());

    assertThat(holdService.hasActiveHold(tripId, userId)).isFalse();
    assertThat(holdService.countActiveHolds(tripId)).isZero();
  }

  // 만료된 hold가 있어도 그 자리는 실제로 비어있으므로 새 hold 발급이 가능해야 함(countActiveHolds가 만료분을 정리)
  @Test
  void tryHold_afterOtherUsersHoldExpired_succeeds() {
    UUID tripId = UUID.randomUUID();
    UUID expiredUser = UUID.randomUUID();
    UUID newUser = UUID.randomUUID();
    String key = "trip:" + tripId + ":holds";
    redisTemplate
        .opsForZSet()
        .add(key, expiredUser.toString(), Instant.now().minusSeconds(60).toEpochMilli());

    boolean held = holdService.tryHold(tripId, newUser, 0, 1);

    assertThat(held).isTrue();
  }

  // #35 Must Have — 정원 1자리 남음 + N명이 동시에 hold 요청 → Lua 스크립트의 원자성으로 정확히 1명만 성공
  @Test
  void tryHold_concurrentRequestsForLastSlot_onlyOneSucceeds() throws InterruptedException {
    UUID tripId = UUID.randomUUID();
    int concurrentUsers = 10;
    List<UUID> userIds = java.util.stream.Stream.generate(UUID::randomUUID)
        .limit(concurrentUsers)
        .toList();

    ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);
    AtomicInteger successCount = new AtomicInteger(0);

    for (UUID userId : userIds) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              if (holdService.tryHold(tripId, userId, 0, 1)) {
                successCount.incrementAndGet();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(finished).isTrue();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(holdService.countActiveHolds(tripId)).isEqualTo(1);
  }

  // Redis 접속 자체가 불가능한 상황(잘못된 포트) — 전부 예외를 던지지 않고 fail-open해야 함
  @Test
  void unreachableRedis_failsOpenInsteadOfThrowing() {
    LettuceConnectionFactory unreachableFactory =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
    unreachableFactory.afterPropertiesSet();
    StringRedisTemplate unreachableTemplate = new StringRedisTemplate(unreachableFactory);
    unreachableTemplate.afterPropertiesSet();
    TripJoinHoldService uncheckedService = new TripJoinHoldService(unreachableTemplate);
    UUID tripId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    boolean held = uncheckedService.tryHold(tripId, userId, 0, 6);
    boolean hasHold = uncheckedService.hasActiveHold(tripId, userId);
    long activeHolds = uncheckedService.countActiveHolds(tripId);

    assertThat(held).isTrue();
    assertThat(hasHold).isFalse();
    assertThat(activeHolds).isZero();
    uncheckedService.release(tripId, userId);
    unreachableFactory.destroy();
  }
}
