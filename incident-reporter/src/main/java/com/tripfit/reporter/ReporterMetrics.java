package com.tripfit.reporter;

import java.util.concurrent.atomic.AtomicLong;

// GET /metrics로 내보내는 리포터 자신의 수치다. Prometheus가 이 엔드포인트를 수집할 수 있는지(up)로
// "리포터 중단" 감시 규칙이 동작하고, 나머지 카운터는 운영 중 리포트가 얼마나 실패하는지 보는 용도다.
public class ReporterMetrics {

  private final AtomicLong reportsSucceeded = new AtomicLong();
  private final AtomicLong reportsFallback = new AtomicLong();
  private final AtomicLong resolvedSent = new AtomicLong();
  private final AtomicLong discordFailures = new AtomicLong();

  public void reportSucceeded() {
    reportsSucceeded.incrementAndGet();
  }

  public void reportFallback() {
    reportsFallback.incrementAndGet();
  }

  public void resolvedSent() {
    resolvedSent.incrementAndGet();
  }

  public void discordFailed() {
    discordFailures.incrementAndGet();
  }

  public String exposition() {
    return """
        # TYPE reporter_up gauge
        reporter_up 1
        # TYPE reporter_reports_total counter
        reporter_reports_total{result="success"} %d
        reporter_reports_total{result="fallback"} %d
        # TYPE reporter_resolved_total counter
        reporter_resolved_total %d
        # TYPE reporter_discord_failures_total counter
        reporter_discord_failures_total %d
        """
        .formatted(reportsSucceeded.get(), reportsFallback.get(), resolvedSent.get(), discordFailures.get());
  }
}
