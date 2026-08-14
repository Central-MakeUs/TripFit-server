package com.tripfit.tripfit.trip.membership.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 참여자가 join 플로우에 진입할 때 정원 자리를 10분간 선점(hold)한다 — 확정 멤버 INSERT 시점에만 정원을 체크하던
// 기존 방식이 남기는 "먼저 완료한 1명만 성공" 레이스를 줄인다. Redis ZSET(trip:{tripId}:holds, score=만료 시각)에
// 원자적 Lua 스크립트로 기록해 동시 요청이 정원을 초과해 hold를 발급하지 않도록 한다. Redis 장애는 전부 fail-open —
// hold 없이 joinTrip()의 기존 카운트 체크로 폴백한다(RedisTokenRevocationChecker와 동일 원칙, decisions/010)
@Component
public class TripJoinHoldService {

  private static final Logger log = LoggerFactory.getLogger(TripJoinHoldService.class);

  private static final Duration HOLD_TTL = Duration.ofMinutes(10);

  // ZSET 자체의 안전망 TTL — hold TTL보다 여유를 둬 매 hold마다 갱신(트립이 완전히 방치돼도 결국 키가 사라지게)
  private static final Duration KEY_SAFETY_TTL = HOLD_TTL.plusMinutes(1);

  private static final DefaultRedisScript<Long> TRY_HOLD_SCRIPT =
      new DefaultRedisScript<>(
          """
              local key = KEYS[1]
              local userId = ARGV[1]
              local nowMillis = tonumber(ARGV[2])
              local expiresAtMillis = tonumber(ARGV[3])
              local safetyTtlSeconds = tonumber(ARGV[4])
              local joinedMemberCount = tonumber(ARGV[5])
              local memberCount = tonumber(ARGV[6])

              redis.call('ZREMRANGEBYSCORE', key, '-inf', nowMillis)

              if redis.call('ZSCORE', key, userId) then
                redis.call('ZADD', key, expiresAtMillis, userId)
                redis.call('EXPIRE', key, safetyTtlSeconds)
                return 1
              end

              local activeHolds = redis.call('ZCARD', key)
              if joinedMemberCount + activeHolds >= memberCount then
                return 0
              end

              redis.call('ZADD', key, expiresAtMillis, userId)
              redis.call('EXPIRE', key, safetyTtlSeconds)
              return 1
              """,
          Long.class);

  private final StringRedisTemplate redisTemplate;

  public TripJoinHoldService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  // 10분 hold 시도 — 이미 hold 중이면 TTL만 갱신(자리 중복 소비 없음), joinedMemberCount+활성 hold 수가
  // memberCount 이상이면 false. 조회부터 기록까지 Lua로 한 번에 처리해 동시 요청 사이 레이스가 없다.
  // Redis 장애 시 fail-open으로 true 반환 — 실제 정원 보장은 joinTrip()의 폴백 카운트 체크가 최종 방어선
  public boolean tryHold(UUID tripId, UUID userId, long joinedMemberCount, int memberCount) {
    try {
      Instant now = Instant.now();
      Long result =
          redisTemplate.execute(
              TRY_HOLD_SCRIPT,
              List.of(holdKey(tripId)),
              userId.toString(),
              String.valueOf(now.toEpochMilli()),
              String.valueOf(now.plus(HOLD_TTL).toEpochMilli()),
              String.valueOf(KEY_SAFETY_TTL.getSeconds()),
              String.valueOf(joinedMemberCount),
              String.valueOf(memberCount));
      return result != null && result == 1L;
    } catch (DataAccessException exception) {
      log.warn("Redis hold 생성 실패 — fail-open으로 통과(실제 정원 보장은 joinTrip() 폴백 체크가 담당)", exception);
      return true;
    }
  }

  // 만료되지 않은 hold 보유 여부 — true면 joinTrip()이 정원 재확인 없이 확정(hold 발급 시 이미 원자적으로 보장됨)
  public boolean hasActiveHold(UUID tripId, UUID userId) {
    try {
      Double score = redisTemplate.opsForZSet().score(holdKey(tripId), userId.toString());
      return score != null && score > Instant.now().toEpochMilli();
    } catch (DataAccessException exception) {
      log.warn("Redis hold 조회 실패 — hold 없음으로 간주해 joinTrip()이 기존 카운트 체크로 폴백", exception);
      return false;
    }
  }

  // 만료 안 된 활성 hold 수 — hold 없이 join하는 호출자의 폴백 정원 체크에 사용
  // (joinedMemberCount + 이 값 ≥ memberCount면 거부해야 다른 사용자의 hold 자리를 가로채지 않음)
  public long countActiveHolds(UUID tripId) {
    try {
      String key = holdKey(tripId);
      long now = Instant.now().toEpochMilli();
      redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now);
      Long count = redisTemplate.opsForZSet().zCard(key);
      return count == null ? 0 : count;
    } catch (DataAccessException exception) {
      log.warn("Redis hold 카운트 조회 실패 — 0으로 간주(과소 추정, fail-open)", exception);
      return 0;
    }
  }

  // 플로우 첫 화면 이탈(명시적 반환) 또는 join 성공 시 hold 소비 — 존재하지 않는 대상 제거도 무해한 no-op
  public void release(UUID tripId, UUID userId) {
    try {
      redisTemplate.opsForZSet().remove(holdKey(tripId), userId.toString());
    } catch (DataAccessException exception) {
      log.warn("Redis hold 해제 실패 — TTL 자연 만료로 결국 정리됨", exception);
    }
  }

  private String holdKey(UUID tripId) {
    return "trip:" + tripId + ":holds";
  }
}
