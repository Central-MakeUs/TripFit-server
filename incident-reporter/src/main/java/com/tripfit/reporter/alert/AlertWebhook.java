package com.tripfit.reporter.alert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

// Grafana 웹훅 본문 전체다. 한 번의 호출에 알림 여러 건(발생·해제가 섞일 수 있음)이 들어올 수 있다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertWebhook(String status, List<Alert> alerts) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public AlertWebhook {
    alerts = alerts == null ? List.of() : List.copyOf(alerts);
  }

  public static AlertWebhook parse(byte[] body) throws IOException {
    return MAPPER.readValue(body, AlertWebhook.class);
  }
}
