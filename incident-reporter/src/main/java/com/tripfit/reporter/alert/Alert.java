package com.tripfit.reporter.alert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

// Grafana가 Alertmanager 형식으로 보내는 알림 한 건이다. 리포터는 이 중 상태·라벨·시각·fingerprint만 쓴다.
// 감시 규칙에는 env·resource·target 라벨이 붙어 있다(docs/specs/cross-cutting/add-prometheus.md "감시 규칙").
@JsonIgnoreProperties(ignoreUnknown = true)
public record Alert(
    String status,
    Map<String, String> labels,
    Map<String, String> annotations,
    String startsAt,
    String endsAt,
    String fingerprint,
    String generatorURL,
    Map<String, Double> values) {

  public Alert {
    labels = labels == null ? Map.of() : Map.copyOf(labels);
    annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    // 리포트에서 A·B·C 순서로 읽히도록 이름순으로 정렬해 둔다.
    values = values == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(values));
  }

  public boolean isFiring() {
    return "firing".equalsIgnoreCase(status);
  }

  public String ruleName() {
    return labels.getOrDefault("alertname", "이름 없는 규칙");
  }

  public String resource() {
    return labels.getOrDefault("resource", "");
  }

  public String target() {
    return labels.getOrDefault("target", "");
  }

  // Grafana가 아직 끝나지 않은 알림의 endsAt을 0001년 같은 값으로 보내기도 해서, 파싱할 수 없으면 now로 본다.
  public Instant startedAt(Instant now) {
    return parseOr(startsAt, now);
  }

  // 해제 메시지에 넣을 지속 시간이다. 해제 시각을 알 수 없으면 now까지로 계산한다.
  public Duration duration(Instant now) {
    Instant start = startedAt(now);
    Instant end = parseOr(endsAt, now);
    if (end.isBefore(start) || end.getEpochSecond() <= 0) {
      end = now;
    }
    return Duration.between(start, end);
  }

  private static Instant parseOr(String value, Instant fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      Instant parsed = Instant.parse(value);
      return parsed.getEpochSecond() <= 0 ? fallback : parsed;
    } catch (DateTimeParseException e) {
      return fallback;
    }
  }
}
