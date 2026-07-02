package com.tripfit.tripfit.auth.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  private String secret;

  private long accessExpirationSeconds = 7200;

  private int refreshExpirationDays = 30;
}
