package com.tripfit.reporter.alert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 같은 장애가 계속 발생 중이면 Grafana가 같은 알림을 반복해서 보낸다. 발생 리포트는 장애 한 건에 한 번만
// 보내야 하므로, fingerprint와 시작 시각을 기억해 두고 해제될 때 지운다.
// 메모리에만 기억하므로 리포터가 재시작되면 진행 중인 장애가 한 번 더 분석될 수 있다(스펙에서 감수).
public class AlertDeduplicator {

  private final Map<String, String> analyzing = new ConcurrentHashMap<>();

  // 처음 보는 발생 알림이면 기억하고 true를 돌려준다. 이미 분석한 알림이면 false다.
  // 같은 fingerprint라도 시작 시각이 다르면 해제 후 다시 발생한 새 장애로 본다.
  public boolean shouldAnalyze(Alert alert) {
    String key = keyOf(alert);
    String startsAt = alert.startsAt() == null ? "" : alert.startsAt();
    String previous = analyzing.put(key, startsAt);
    return !startsAt.equals(previous);
  }

  public void forget(Alert alert) {
    analyzing.remove(keyOf(alert));
  }

  // Grafana는 fingerprint를 항상 채워 보내지만, 없을 때도 규칙 이름으로 중복을 막는다.
  private static String keyOf(Alert alert) {
    String fingerprint = alert.fingerprint();
    return fingerprint == null || fingerprint.isBlank() ? "rule:" + alert.ruleName() : fingerprint;
  }
}
