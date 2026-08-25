package com.tripfit.tripfit.auth.jwt;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

// tripfit.jwt.* — 기본 TTL은 스펙(access 2h / refresh 30d). secret은 env
@Data
@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  // toString에 노출되면 JWT 서명 시크릿이 로그로 유출될 수 있어 제외
  @ToString.Exclude
  private String secret;

  private long accessExpirationSeconds = 7200;

  private int refreshExpirationDays = 30;
}
