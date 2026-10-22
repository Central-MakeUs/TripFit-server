package com.tripfit.reporter.collect;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// Prometheus HTTP API(/api/v1/query, /api/v1/query_range)를 부른다. 결과는 라벨과 값만 남긴 단순한 형태로 바꾼다.
public class PrometheusClient {

  public record Sample(Map<String, String> labels, double value) {}

  public record Series(Map<String, String> labels, List<Double> values) {

    public double min() {
      return values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    public double max() {
      return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    public double last() {
      return values.isEmpty() ? Double.NaN : values.get(values.size() - 1);
    }
  }

  private final URI baseUrl;
  private final HttpJson http;

  public PrometheusClient(URI baseUrl, HttpClient client) {
    this.baseUrl = baseUrl;
    this.http = new HttpJson(client);
  }

  public List<Sample> query(String expr, Instant at) throws IOException {
    JsonNode root =
        http.get(baseUrl, "/api/v1/query", Map.of("query", expr, "time", seconds(at)));
    List<Sample> samples = new ArrayList<>();
    for (JsonNode result : root.path("data").path("result")) {
      double value = parse(result.path("value").path(1).asText());
      if (!Double.isNaN(value)) {
        samples.add(new Sample(labels(result.path("metric")), value));
      }
    }
    return samples;
  }

  public List<Series> queryRange(String expr, Instant start, Instant end, int stepSeconds)
      throws IOException {
    JsonNode root =
        http.get(
            baseUrl,
            "/api/v1/query_range",
            Map.of(
                "query", expr,
                "start", seconds(start),
                "end", seconds(end),
                "step", Integer.toString(stepSeconds)));
    List<Series> series = new ArrayList<>();
    for (JsonNode result : root.path("data").path("result")) {
      List<Double> values = new ArrayList<>();
      for (JsonNode point : result.path("values")) {
        double value = parse(point.path(1).asText());
        if (!Double.isNaN(value)) {
          values.add(value);
        }
      }
      series.add(new Series(labels(result.path("metric")), values));
    }
    return series;
  }

  private static String seconds(Instant instant) {
    return Long.toString(instant.getEpochSecond());
  }

  // Prometheus는 값을 문자열로 준다. "NaN"·"+Inf"처럼 리포트에 쓸 수 없는 값은 버린다.
  private static double parse(String text) {
    try {
      double value = Double.parseDouble(text);
      return Double.isInfinite(value) ? Double.NaN : value;
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private static Map<String, String> labels(JsonNode metric) {
    Map<String, String> labels = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = metric.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      labels.put(field.getKey(), field.getValue().asText());
    }
    return labels;
  }
}
