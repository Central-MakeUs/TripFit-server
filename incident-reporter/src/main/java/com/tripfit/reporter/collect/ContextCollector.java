package com.tripfit.reporter.collect;

import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.collect.IncidentContext.EndpointStat;
import com.tripfit.reporter.collect.IncidentContext.MetricSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 알림 한 건에 대해 Prometheus 수치, Loki 로그, 관련 코드를 모은다. 한 곳에서 실패해도 나머지는 계속 모으고,
// 실패한 항목은 사유와 함께 missing에 남긴다. 리포트가 자료 일부 없이 나가더라도 알림 자체는 반드시 나가야 한다.
public class ContextCollector {

  static final Duration LOOKBACK = Duration.ofMinutes(10);
  // 오래 이어진 알림이 재분석될 때 조회 구간이 Prometheus 한도(점 11,000개)를 넘지 않도록 상한을 둔다.
  static final Duration MAX_WINDOW = Duration.ofHours(2);
  static final int STEP_SECONDS = 30;
  static final int TOP_ENDPOINTS = 5;
  static final int MAX_CODE_ENDPOINTS = 2;

  // 감시 규칙 라벨(resource·target)과 기록 규칙 이름의 대응이다. 기록 규칙 정의는
  // docs/specs/cross-cutting/add-prometheus.md "환경별 차이를 흡수하는 방법" 표가 SSOT다.
  private static final Map<String, String[]> RULE_METRICS =
      Map.of(
          "app/cpu", new String[] {"tripfit:app_cpu_usage:ratio", "앱 CPU 사용률(0~1)"},
          "app/memory", new String[] {"tripfit:app_heap_usage:ratio", "앱 JVM 힙 사용률(0~1)"},
          "db/cpu", new String[] {"tripfit:db_cpu_usage:ratio", "DB 서버 CPU 사용률(0~1)"},
          "db/memory", new String[] {"tripfit:db_memory_usage:ratio", "DB 서버 메모리 사용률(0~1)"},
          "db/db_pool", new String[] {"tripfit:db_pool_pending", "DB 커넥션을 기다리는 요청 수"},
          "app/db_pool", new String[] {"tripfit:db_pool_pending", "DB 커넥션을 기다리는 요청 수"});

  private static final String[][] POOL_METRICS = {
    {"sum(hikaricp_connections_active)", "사용 중인 DB 커넥션 수"},
    {"sum(hikaricp_connections_pending)", "DB 커넥션을 기다리는 요청 수"},
    {"sum(hikaricp_connections_max)", "DB 커넥션 최대 수"}
  };

  private static final String TOP_ENDPOINTS_QUERY =
      "topk(" + TOP_ENDPOINTS + ", sum by (method, uri) (rate(http_server_requests_seconds_count{uri=~\"/api/.*\"}[5m])))";
  private static final String P95_QUERY =
      "histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_seconds_bucket{uri=~\"/api/.*\"}[5m])))";

  private final PrometheusClient prometheus;
  private final LokiClient loki;
  private final SourceCodeLocator code;
  private final Clock clock;

  public ContextCollector(PrometheusClient prometheus, LokiClient loki, SourceCodeLocator code, Clock clock) {
    this.prometheus = prometheus;
    this.loki = loki;
    this.code = code;
    this.clock = clock;
  }

  public IncidentContext collect(Alert alert) {
    Instant now = clock.instant();
    Instant start = alert.startedAt(now).minus(LOOKBACK);
    if (start.isBefore(now.minus(MAX_WINDOW))) {
      start = now.minus(MAX_WINDOW);
    }
    if (!start.isBefore(now)) {
      start = now.minus(LOOKBACK);
    }
    List<String> missing = new ArrayList<>();

    List<MetricSummary> metrics = collectMetrics(alert, start, now, missing);
    List<EndpointStat> endpoints = collectEndpoints(now, missing);
    List<String> logs = collectLogs(start, now, missing);
    List<SourceCodeLocator.Snippet> snippets = collectCode(endpoints, missing);
    return new IncidentContext(metrics, endpoints, logs, snippets, missing);
  }

  private List<MetricSummary> collectMetrics(Alert alert, Instant start, Instant end, List<String> missing) {
    List<String[]> wanted = new ArrayList<>();
    String[] ruleMetric = RULE_METRICS.get(alert.target() + "/" + alert.resource());
    if (ruleMetric != null) {
      wanted.add(ruleMetric);
    }
    wanted.addAll(List.of(POOL_METRICS));

    List<MetricSummary> summaries = new ArrayList<>();
    for (String[] metric : wanted) {
      try {
        for (PrometheusClient.Series series : prometheus.queryRange(metric[0], start, end, STEP_SECONDS)) {
          summaries.add(new MetricSummary(metric[0], metric[1], series.min(), series.max(), series.last()));
        }
      } catch (Exception e) {
        missing.add("수치 조회 실패: " + metric[0] + " (" + e.getMessage() + ")");
      }
    }
    return summaries;
  }

  private List<EndpointStat> collectEndpoints(Instant at, List<String> missing) {
    try {
      Map<String, Double> p95ByEndpoint = new HashMap<>();
      try {
        for (PrometheusClient.Sample sample : prometheus.query(P95_QUERY, at)) {
          p95ByEndpoint.put(key(sample.labels()), sample.value());
        }
      } catch (Exception e) {
        missing.add("p95 응답 시간 조회 실패 (" + e.getMessage() + ")");
      }
      List<EndpointStat> stats = new ArrayList<>();
      for (PrometheusClient.Sample sample : prometheus.query(TOP_ENDPOINTS_QUERY, at)) {
        Map<String, String> labels = sample.labels();
        stats.add(
            new EndpointStat(
                labels.getOrDefault("method", ""),
                labels.getOrDefault("uri", ""),
                sample.value(),
                p95ByEndpoint.getOrDefault(key(labels), Double.NaN)));
      }
      stats.sort((a, b) -> Double.compare(b.requestsPerSecond(), a.requestsPerSecond()));
      return stats;
    } catch (Exception e) {
      missing.add("요청 많은 API 조회 실패 (" + e.getMessage() + ")");
      return List.of();
    }
  }

  private List<String> collectLogs(Instant start, Instant end, List<String> missing) {
    try {
      return loki.errorLines(start, end);
    } catch (Exception e) {
      missing.add("로그 조회 실패 (" + e.getMessage() + ")");
      return List.of();
    }
  }

  // 요청이 가장 많은 API 두 개만 코드를 붙인다. 코드를 너무 많이 넣으면 모델이 핵심을 놓치기 쉽다.
  private List<SourceCodeLocator.Snippet> collectCode(List<EndpointStat> endpoints, List<String> missing) {
    code.refresh().ifPresent(missing::add);
    List<SourceCodeLocator.Snippet> snippets = new ArrayList<>();
    for (EndpointStat endpoint : endpoints.stream().limit(MAX_CODE_ENDPOINTS).toList()) {
      try {
        List<SourceCodeLocator.Snippet> found = code.locate(endpoint.method(), endpoint.uri());
        if (found.isEmpty()) {
          missing.add("코드 위치를 찾지 못한 API: " + endpoint.method() + " " + endpoint.uri());
        }
        snippets.addAll(found);
      } catch (Exception e) {
        missing.add("코드 읽기 실패: " + endpoint.uri() + " (" + e.getMessage() + ")");
      }
    }
    return snippets;
  }

  private static String key(Map<String, String> labels) {
    return labels.getOrDefault("method", "") + " " + labels.getOrDefault("uri", "");
  }
}
