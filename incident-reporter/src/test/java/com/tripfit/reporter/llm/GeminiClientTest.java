package com.tripfit.reporter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 기본 모델이 503·429면 예비 모델로 한 번 더 묻는지 가짜 Gemini 서버로 확인한다.
class GeminiClientTest {

  private static final String OK =
      """
      {"candidates": [{"content": {"parts": [{"text":
        "{\\"summary\\": \\"요약\\", \\"evidence\\": [], \\"suspects\\": [], \\"actions\\": [], \\"confidence\\": \\"LOW\\"}"}]}}]}
      """;
  private static final String OVERLOADED = "{\"error\": {\"code\": 503, \"status\": \"UNAVAILABLE\", \"message\": \"high demand\"}}";

  private final List<String> calledModels = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private URI baseUrl;
  // 모델 이름별로 돌려줄 상태 코드다. 없으면 200을 준다.
  private Map<String, Integer> statusByModel = Map.of();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      String model = path.substring("/v1beta/models/".length(), path.indexOf(':'));
      calledModels.add(model);
      int status = statusByModel.getOrDefault(model, 200);
      byte[] body = (status == 200 ? OK : OVERLOADED).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private GeminiClient client(String fallback) {
    return new GeminiClient(baseUrl, "primary", fallback, "test-key", HttpClient.newHttpClient());
  }

  @Test
  void analyze_primaryOverloaded_usesFallbackModel() throws Exception {
    statusByModel = Map.of("primary", 503);

    IncidentReport report = client("backup").analyze("s", "u");

    assertThat(report.summary()).isEqualTo("요약");
    assertThat(calledModels).containsExactly("primary", "backup");
  }

  @Test
  void analyze_primaryRateLimited_usesFallbackModel() throws Exception {
    statusByModel = Map.of("primary", 429);

    client("backup").analyze("s", "u");

    assertThat(calledModels).containsExactly("primary", "backup");
  }

  @Test
  void analyze_bothOverloaded_throwsWithBothReasons() {
    statusByModel = Map.of("primary", 503, "backup", 503);

    assertThatThrownBy(() -> client("backup").analyze("s", "u"))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("primary")
        .hasMessageContaining("예비 모델 backup");
  }

  @Test
  void analyze_badRequest_doesNotTryFallback() {
    statusByModel = Map.of("primary", 400);

    assertThatThrownBy(() -> client("backup").analyze("s", "u")).isInstanceOf(LlmException.class);
    assertThat(calledModels).containsExactly("primary");
  }

  @Test
  void analyze_noFallbackConfigured_failsOnce() {
    statusByModel = Map.of("primary", 503);

    assertThatThrownBy(() -> client("").analyze("s", "u")).isInstanceOf(LlmException.class);
    assertThat(calledModels).containsExactly("primary");
  }
}
