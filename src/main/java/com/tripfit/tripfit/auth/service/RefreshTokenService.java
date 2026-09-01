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

  // 새로운 Refresh Token을 발급합니다.
  // 동일 기기/로그인 세션을 식별하는 Family ID를 새로 생성하여 토큰과 함께 저장합니다.
  public IssuedRefreshToken create(UUID userId) {
    return issue(userId, UUID.randomUUID().toString());
  }

  // 기존 Refresh Token을 사용하여 새로운 토큰으로 교체(Rotate)합니다.
  // 1. 기존 토큰이 유효한지 검증합니다.
  // 2. 이미 사용된 토큰(Reuse)이라면 보안을 위해 해당 Family 전체를 폐기 처리합니다.
  // 3. 정상적인 경우, 새 토큰을 발급하고 기존 토큰을 무효화합니다.
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
        "Refresh token reuse detected. family revoked. userId={}, familyId={}",
        entry.userId(),
        entry.familyId());
    throw new TripFitException(AuthErrorCode.AUTH_REFRESH_REUSE);
  }

  public void delete(String tokenValue) {
    String activeValue = redisTemplate.opsForValue().get(ACTIVE_PREFIX + tokenValue);
    if (activeValue == null) {
      return;
    }
    Entry entry = Entry.parse(activeValue);
    redisTemplate.delete(ACTIVE_PREFIX + tokenValue);
    redisTemplate.delete(FAMILY_PREFIX + entry.familyId());
  }

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
