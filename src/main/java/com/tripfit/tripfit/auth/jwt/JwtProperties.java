package com.tripfit.tripfit.auth.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// tripfit.jwt.* — 기본 TTL은 스펙(access 2h / refresh 30d). secret은 env
@Data
@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  private String secret;

  private long accessExpirationSeconds = 7200;

  private int refreshExpirationDays = 30;
}
