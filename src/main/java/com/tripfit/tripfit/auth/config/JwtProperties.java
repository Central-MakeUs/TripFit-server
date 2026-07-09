package com.tripfit.tripfit.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.jwt")
public class JwtProperties {

  private String secret;

  private long accessExpirationSeconds = 7200;

  private int refreshExpirationDays = 30;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getAccessExpirationSeconds() {
    return accessExpirationSeconds;
  }

  public void setAccessExpirationSeconds(long accessExpirationSeconds) {
    this.accessExpirationSeconds = accessExpirationSeconds;
  }

  public int getRefreshExpirationDays() {
    return refreshExpirationDays;
  }

  public void setRefreshExpirationDays(int refreshExpirationDays) {
    this.refreshExpirationDays = refreshExpirationDays;
  }
}
