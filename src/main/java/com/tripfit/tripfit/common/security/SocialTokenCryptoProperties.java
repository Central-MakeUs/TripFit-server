package com.tripfit.tripfit.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.security")
public class SocialTokenCryptoProperties {

  private String tokenAesKey = "";

  public String getTokenAesKey() {
    return tokenAesKey;
  }

  public void setTokenAesKey(String tokenAesKey) {
    this.tokenAesKey = tokenAesKey;
  }
}
