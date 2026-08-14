package com.tripfit.tripfit.auth.oauth;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// access token jti 블랙리스트 — Redis 조회·등록 실패는 전부 fail-open(차단하지 않음) 처리한다. 즉시무효화는
// 보조 방어선일 뿐이고, Redis(EC2 D) 장애로 로그인 전체가 막히는 것이 더 큰 손해이기 때문(decisions/004·010)
@Component
public class RedisTokenRevocationChecker implements TokenRevocationChecker {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationChecker.class);

  private static final String KEY_PREFIX = "auth:bl:";

  private final StringRedisTemplate redisTemplate;

  public RedisTokenRevocationChecker(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean isRevoked(String jti) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    } catch (DataAccessException exception) {
      log.warn("Redis 블랙리스트 조회 실패 — fail-open으로 통과", exception);
      return false;
    }
  }

  // logout·탈퇴 시 access token을 즉시 무효화 — 이미 만료된 토큰은 등록할 필요 없음(TTL<=0)
  public void revoke(String jti, Instant tokenExpiresAt) {
    long remainingSeconds = Duration.between(Instant.now(), tokenExpiresAt).getSeconds();
    if (remainingSeconds <= 0) {
      return;
    }
    try {
      redisTemplate
          .opsForValue()
          .set(KEY_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
    } catch (DataAccessException exception) {
      log.warn("Redis 블랙리스트 등록 실패 — 이 토큰의 즉시무효화만 스킵되고 자연 만료까지는 유효함", exception);
    }
  }
}
