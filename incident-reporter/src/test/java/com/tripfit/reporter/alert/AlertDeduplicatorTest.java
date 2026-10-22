package com.tripfit.reporter.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertDeduplicatorTest {

  private final AlertDeduplicator deduplicator = new AlertDeduplicator();

  private static Alert firing(String fingerprint, String startsAt) {
    return new Alert("firing", Map.of("alertname", "앱 CPU"), null, startsAt, null, fingerprint, null, null);
  }

  @Test
  void shouldAnalyze_sameAlertRepeated_onlyFirstTime() {
    assertThat(deduplicator.shouldAnalyze(firing("f1", "2026-10-19T09:00:00Z"))).isTrue();
    assertThat(deduplicator.shouldAnalyze(firing("f1", "2026-10-19T09:00:00Z"))).isFalse();
  }

  @Test
  void shouldAnalyze_afterForget_analyzesAgain() {
    Alert alert = firing("f1", "2026-10-19T09:00:00Z");
    deduplicator.shouldAnalyze(alert);
    deduplicator.forget(alert);

    assertThat(deduplicator.shouldAnalyze(alert)).isTrue();
  }

  @Test
  void shouldAnalyze_sameFingerprintNewStart_treatedAsNewIncident() {
    deduplicator.shouldAnalyze(firing("f1", "2026-10-19T09:00:00Z"));

    assertThat(deduplicator.shouldAnalyze(firing("f1", "2026-10-19T11:00:00Z"))).isTrue();
  }
}
