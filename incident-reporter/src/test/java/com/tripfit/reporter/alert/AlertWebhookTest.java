package com.tripfit.reporter.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertWebhookTest {

  // Grafana 10.4 웹훅이 보내는 본문의 모양이다. 리포터가 쓰지 않는 필드도 섞어 두어 무시되는지 함께 본다.
  private static final String PAYLOAD =
      """
      {
        "receiver": "incident-reporter",
        "status": "firing",
        "orgId": 1,
        "alerts": [
          {
            "status": "firing",
            "labels": {"alertname": "앱 CPU", "env": "prod", "resource": "cpu", "target": "app"},
            "annotations": {"summary": "앱 CPU 사용률이 임계점을 넘었습니다"},
            "startsAt": "2026-10-19T09:00:00Z",
            "endsAt": "0001-01-01T00:00:00Z",
            "generatorURL": "https://grafana.tripfit.online/alerting/grafana/abc/view",
            "fingerprint": "f1",
            "silenceURL": "https://grafana.tripfit.online/alerting/silence/new",
            "values": {"A": 0.93, "B": 1}
          },
          {
            "status": "resolved",
            "labels": {"alertname": "DB 커넥션 풀 대기", "resource": "db_pool", "target": "db"},
            "startsAt": "2026-10-19T08:00:00Z",
            "endsAt": "2026-10-19T08:12:00Z",
            "fingerprint": "f2"
          }
        ],
        "groupLabels": {},
        "commonLabels": {},
        "externalURL": "https://grafana.tripfit.online/",
        "version": "1"
      }
      """;

  @Test
  void parse_firingAndResolvedInOneCall_readsBoth() throws Exception {
    AlertWebhook webhook = AlertWebhook.parse(PAYLOAD.getBytes(StandardCharsets.UTF_8));

    assertThat(webhook.alerts()).hasSize(2);
    Alert firing = webhook.alerts().get(0);
    assertThat(firing.isFiring()).isTrue();
    assertThat(firing.ruleName()).isEqualTo("앱 CPU");
    assertThat(firing.resource()).isEqualTo("cpu");
    assertThat(firing.target()).isEqualTo("app");
    assertThat(firing.values()).containsEntry("A", 0.93);

    Alert resolved = webhook.alerts().get(1);
    assertThat(resolved.isFiring()).isFalse();
    assertThat(resolved.annotations()).isEmpty();
    assertThat(resolved.values()).isEmpty();
  }

  @Test
  void duration_resolvedAlert_usesEndsAt() throws Exception {
    Alert resolved = AlertWebhook.parse(PAYLOAD.getBytes(StandardCharsets.UTF_8)).alerts().get(1);

    assertThat(resolved.duration(Instant.parse("2026-10-19T10:00:00Z"))).isEqualTo(Duration.ofMinutes(12));
  }

  @Test
  void duration_firingAlertWithZeroEndsAt_countsUntilNow() throws Exception {
    Alert firing = AlertWebhook.parse(PAYLOAD.getBytes(StandardCharsets.UTF_8)).alerts().get(0);

    assertThat(firing.duration(Instant.parse("2026-10-19T09:05:00Z"))).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void startedAt_unparsableStartsAt_fallsBackToNow() {
    Instant now = Instant.parse("2026-10-19T09:00:00Z");
    Alert alert = new Alert("firing", null, null, "not-a-time", null, "f", null, null);

    assertThat(alert.startedAt(now)).isEqualTo(now);
  }
}
