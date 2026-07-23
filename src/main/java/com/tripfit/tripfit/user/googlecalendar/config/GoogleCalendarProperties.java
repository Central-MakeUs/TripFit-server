package com.tripfit.tripfit.user.googlecalendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.google-calendar")
public class GoogleCalendarProperties {

  private String tokenAesKey = "";

  public String getTokenAesKey() {
    return tokenAesKey;
  }

  public void setTokenAesKey(String tokenAesKey) {
    this.tokenAesKey = tokenAesKey;
  }
}
