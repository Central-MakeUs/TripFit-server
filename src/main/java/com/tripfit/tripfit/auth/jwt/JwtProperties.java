package com.tripfit.tripfit.auth.jwt;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

// tripfit.jwt.* — 기본 TTL은 스펙(access 15분 / refresh 30일). access token 블랙리스트를 폐기해
// 무상태성을 강화하는 대신, 로그아웃·탈퇴해도 즉시 무효화되지 않는 노출 창을 짧게 유지하기 위해 access
// TTL을 2시간에서 줄였다(docs/specs/auth/auth-refresh-redis-cookie.md). secret은 env
@Data
@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  // toString에 노출되면 JWT 서명 시크릿이 로그로 유출될 수 있어 제외
  @ToString.Exclude
  private String secret;

  private long accessExpirationSeconds = 900;

  private int refreshExpirationDays = 30;
}
