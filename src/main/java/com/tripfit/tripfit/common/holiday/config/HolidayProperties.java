package com.tripfit.tripfit.common.holiday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripfit.holiday")
public class HolidayProperties {

  // 공공데이터포털 인증키 — 포털이 Encoding/Decoding 두 형태를 함께 발급하는데, 여기에는 반드시 **Decoding**
  // 키를 넣는다. Encoding 키를 넣으면 RestClient가 한 번 더 percent-encoding 해 인증에 실패한다
  private String serviceKey = "";

  public String getServiceKey() {
    return serviceKey;
  }

  public void setServiceKey(String serviceKey) {
    this.serviceKey = serviceKey;
  }
}
