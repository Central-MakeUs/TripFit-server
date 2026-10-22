package com.tripfit.reporter.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tripfit.reporter.ReporterMetrics;
import java.io.IOException;

// GET /metrics — Prometheus가 리포터 생존과 리포트 성공·실패 수를 수집하는 엔드포인트다.
public class MetricsHandler implements HttpHandler {

  private final ReporterMetrics metrics;

  public MetricsHandler(ReporterMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!"GET".equals(exchange.getRequestMethod())) {
        AlertsHandler.respond(exchange, 405, "method not allowed");
        return;
      }
      AlertsHandler.respond(exchange, 200, metrics.exposition());
    }
  }
}
