package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// refresh token을 Redis에 저장하는 RTR(Refresh Token Rotation) 구현체 — MySQL 대신 Redis가 SSOT다
// (docs/specs/auth/auth-refresh-redis-cookie.md). 키 4종으로 DB의 revoked_at 컬럼이 하던
// "폐기됨과 애초에 없음의 구분"(재사용 탐지)을 그대로 재현한다:
// auth:refresh:active:{token} — 현재 유효한 토큰 1건 (값: "userId|familyId")
// auth:refresh:family:{id} — 그 family의 현재 active 토큰 값(재사용 탐지 시 회수 대상 조회용)
// auth:refresh:revoked:{token} — rotate로 소비된 토큰의 tombstone (값: "userId|familyId")
// auth:refresh:user:{userId} — 그 유저가 가진 familyId 집합(탈퇴 시 일괄 회수용 — 개별 family가 자연
// 만료돼도 이 집합엔 stale하게 남을 수 있지만, 삭제 시도만 하는 no-op이라 무해)
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private static final String ACTIVE_PREFIX = "auth:refresh:active:";

  private static final String FAMILY_PREFIX = "auth:refresh:family:";

  private static final String REVOKED_PREFIX = "auth:refresh:revoked:";

  private static final String USER_FAMILIES_PREFIX = "auth:refresh:user:";

  private static final String DELIMITER = "|";

  private final StringRedisTemplate redisTemplate;

  private final JwtProperties jwtProperties;

  public IssuedRefreshToken create(UUID userId) {
    return issue(userId, UUID.randomUUID().toString());
  }

  // 1. tombstone 존재 → 이미 rotate로 소비된 토큰이 재제출됨(탈취 재사용 의심) → 그 family의 현재 active
  // 토큰까지 회수하고 예외
  // 2. active 없음 → 애초에 없거나 자연 만료(Redis TTL로 소멸) → AUTH_INVALID_REFRESH
  // 3. 있으면 정상 rotate — tombstone을 남기고 같은 family로 재발급
  public IssuedRefreshToken rotate(String tokenValue) {
    String revokedValue = redisTemplate.opsForValue().get(REVOKED_PREFIX + tokenValue);
    if (revokedValue != null) {
      handleReuse(revokedValue);
    }

    String activeValue = redisTemplate.opsForValue().get(ACTIVE_PREFIX + tokenValue);
    if (activeValue == null) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH);
    }
    Entry entry = Entry.parse(activeValue);

    Duration ttl = Duration.ofDays(jwtProperties.getRefreshExpirationDays());
    redisTemplate.opsForValue().set(REVOKED_PREFIX + tokenValue, activeValue, ttl);
    redisTemplate.delete(ACTIVE_PREFIX + tokenValue);

    return issue(entry.userId(), entry.familyId());
  }

  private void handleReuse(String revokedValue) {
    Entry entry = Entry.parse(revokedValue);
    String activeToken = redisTemplate.opsForValue().get(FAMILY_PREFIX + entry.familyId());
    if (activeToken != null) {
      redisTemplate.delete(ACTIVE_PREFIX + activeToken);
    }
    redisTemplate.delete(FAMILY_PREFIX + entry.familyId());
    log.warn(
        "Refresh token reuse detected — family revoked. userId={}, familyId={}",
        entry.userId(),
        entry.familyId());
    throw new TripFitException(AuthErrorCode.AUTH_REFRESH_REUSE);
  }

  // 로그아웃 — 이 토큰 하나만 회수한다(같은 family의 다른 기기 세션은 그대로 유효). 이미 없거나
  // 만료된 토큰이면 조용히 무시(로그아웃 자체는 항상 성공해야 함)
  public void delete(String tokenValue) {
    String activeValue = redisTemplate.opsForValue().get(ACTIVE_PREFIX + tokenValue);
    if (activeValue == null) {
      return;
    }
    Entry entry = Entry.parse(activeValue);
    redisTemplate.delete(ACTIVE_PREFIX + tokenValue);
    redisTemplate.delete(FAMILY_PREFIX + entry.familyId());
  }

  // 탈퇴 — 이 유저가 가진 모든 로그인 체인(family)을 한꺼번에 회수
  public void revokeAllForUser(UUID userId) {
    String userKey = USER_FAMILIES_PREFIX + userId;
    Set<String> familyIds = redisTemplate.opsForSet().members(userKey);
    if (familyIds != null) {
      for (String familyId : familyIds) {
        String activeToken = redisTemplate.opsForValue().get(FAMILY_PREFIX + familyId);
        if (activeToken != null) {
          redisTemplate.delete(ACTIVE_PREFIX + activeToken);
        }
        redisTemplate.delete(FAMILY_PREFIX + familyId);
      }
    }
    redisTemplate.delete(userKey);
  }

  private IssuedRefreshToken issue(UUID userId, String familyId) {
    String token = UUID.randomUUID().toString();
    Duration ttl = Duration.ofDays(jwtProperties.getRefreshExpirationDays());
    String value = userId + DELIMITER + familyId;

    redisTemplate.opsForValue().set(ACTIVE_PREFIX + token, value, ttl);
    redisTemplate.opsForValue().set(FAMILY_PREFIX + familyId, token, ttl);
    String userKey = USER_FAMILIES_PREFIX + userId;
    redisTemplate.opsForSet().add(userKey, familyId);
    redisTemplate.expire(userKey, ttl);

    return new IssuedRefreshToken(token, userId, familyId);
  }

  private record Entry(
      UUID userId,
      String familyId
  ) {
    static Entry parse(String value) {
      String[] parts = value.split("\\" + DELIMITER, 2);
      return new Entry(UUID.fromString(parts[0]), parts[1]);
    }
  }
}
