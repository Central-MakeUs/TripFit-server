package com.tripfit.tripfit.common.holiday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.holiday")
public class HolidayProperties {

  private String serviceKey = "";

  public String getServiceKey() {
    return serviceKey;
  }

  public void setServiceKey(String serviceKey) {
    this.serviceKey = serviceKey;
  }
}
