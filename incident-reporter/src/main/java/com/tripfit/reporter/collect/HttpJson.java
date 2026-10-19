package com.tripfit.reporter.collect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

// Prometheus·Loki 조회에 공통으로 쓰는 GET + JSON 파싱이다. 한 조회가 느려도 리포트 전체가 멈추지 않게
// 요청마다 짧은 시간 상한을 둔다.
final class HttpJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient client;

  HttpJson(HttpClient client) {
    this.client = client;
  }

  JsonNode get(URI base, String path, Map<String, String> params) throws IOException {
    String query =
        params.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base + path + "?" + query)).timeout(TIMEOUT).GET().build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IOException(path + " 응답 " + response.statusCode());
      }
      return MAPPER.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(path + " 조회가 중단됐습니다", e);
    }
  }
}
