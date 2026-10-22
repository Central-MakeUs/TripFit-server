package com.tripfit.reporter.collect;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 알림 구간의 앱 ERROR·WARN 로그를 Loki에서 가져온다. 로그 셀렉터는 환경마다 라벨 이름이 다를 수 있어
// LOKI_QUERY 환경 변수로 받는다.
public class LokiClient {

  static final int MAX_LINES = 200;
  static final int MAX_LINE_LENGTH = 500;

  private record Line(long timestampNanos, String text) {}

  private final URI baseUrl;
  private final String query;
  private final HttpJson http;

  public LokiClient(URI baseUrl, String query, HttpClient client) {
    this.baseUrl = baseUrl;
    this.query = query;
    this.http = new HttpJson(client);
  }

  // 최근 것부터 최대 200줄을 받아, 읽기 쉽게 오래된 것부터 다시 정렬해 돌려준다.
  public List<String> errorLines(Instant start, Instant end) throws IOException {
    JsonNode root =
        http.get(
            baseUrl,
            "/loki/api/v1/query_range",
            Map.of(
                "query", query,
                "start", nanos(start),
                "end", nanos(end),
                "limit", Integer.toString(MAX_LINES),
                "direction", "backward"));
    List<Line> lines = new ArrayList<>();
    for (JsonNode stream : root.path("data").path("result")) {
      for (JsonNode value : stream.path("values")) {
        lines.add(new Line(value.path(0).asLong(), truncate(value.path(1).asText())));
      }
    }
    return lines.stream()
        .sorted(Comparator.comparingLong(Line::timestampNanos).reversed())
        .limit(MAX_LINES)
        .sorted(Comparator.comparingLong(Line::timestampNanos))
        .map(Line::text)
        .toList();
  }

  private static String nanos(Instant instant) {
    return Long.toString(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
  }

  private static String truncate(String line) {
    return line.length() <= MAX_LINE_LENGTH ? line : line.substring(0, MAX_LINE_LENGTH) + "…";
  }
}
