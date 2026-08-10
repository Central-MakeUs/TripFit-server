package com.tripfit.tripfit.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.fcm")
public class FcmProperties {

  private String credentialsBase64 = "";

  public String getCredentialsBase64() {
    return credentialsBase64;
  }

  public void setCredentialsBase64(String credentialsBase64) {
    this.credentialsBase64 = credentialsBase64;
  }
}
