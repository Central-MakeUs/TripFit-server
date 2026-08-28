package com.tripfit.tripfit.auth.jwt;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  @ToString.Exclude
  private String secret;

  private long accessExpirationSeconds = 900;

  private int refreshExpirationDays = 30;
}
