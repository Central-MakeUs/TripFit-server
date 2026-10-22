package com.tripfit.reporter.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Gemini API의 generateContent를 JSON 응답 형식으로 부른다. 기본 모델이 과부하(503)·한도 초과(429)면
// 예비 모델로 한 번 더 묻는다(2026-10-19 사용자 결정 — 무료 모델은 수요가 몰리면 503을 준다).
// 키는 URL이 아니라 x-goog-api-key 헤더로 보낸다
// (URL에 넣으면 프록시·접근 로그에 남을 수 있다). 근거: https://ai.google.dev/api/generate-content (2026-10-19 확인)
public class GeminiClient implements LlmClient {

  // 예비 모델까지 두 번 물어도 리포터의 전체 시간 상한(60초) 안에 들어오도록 한 번은 25초로 끊는다.
  static final Duration TIMEOUT = Duration.ofSeconds(25);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final URI baseUrl;
  private final String model;
  private final String fallbackModel;
  private final String apiKey;
  private final HttpClient client;

  public GeminiClient(URI baseUrl, String model, String fallbackModel, String apiKey, HttpClient client) {
    this.baseUrl = baseUrl;
    this.model = model;
    this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
    this.apiKey = apiKey;
    this.client = client;
  }

  @Override
  public IncidentReport analyze(String systemPrompt, String userPrompt) throws LlmException {
    try {
      return call(model, systemPrompt, userPrompt);
    } catch (LlmException first) {
      if (!first.retryable() || fallbackModel.isEmpty() || fallbackModel.equals(model)) {
        throw first;
      }
      try {
        return call(fallbackModel, systemPrompt, userPrompt);
      } catch (LlmException second) {
        throw new LlmException(first.getMessage() + " / 예비 모델 " + fallbackModel + ": " + second.getMessage());
      }
    }
  }

  private IncidentReport call(String modelName, String systemPrompt, String userPrompt) throws LlmException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + modelName + ":generateContent"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(systemPrompt, userPrompt)))
            .build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new LlmException("Gemini 호출 실패(" + e.getClass().getSimpleName() + ")", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmException("Gemini 호출이 중단됐습니다", e);
    }
    int status = response.statusCode();
    if (status == 429) {
      throw new LlmException(modelName + " 호출 한도 초과(429" + errorDetail(response.body()) + ")", true);
    }
    if (status == 503) {
      throw new LlmException(modelName + " 일시적 과부하(503" + errorDetail(response.body()) + ")", true);
    }
    if (status == 401 || status == 403) {
      throw new LlmException("Gemini API 키가 거절됐습니다(" + status + errorDetail(response.body()) + ")");
    }
    if (status / 100 != 2) {
      throw new LlmException(modelName + " 응답 오류(" + status + errorDetail(response.body()) + ")");
    }
    return GeminiResponseParser.parse(response.body());
  }

  // Google 오류 응답의 error.status와 error.message 앞부분만 사유로 붙인다. 원인을 알 수 있을 만큼만 남기고 길게 싣지 않는다.
  static String errorDetail(String body) {
    try {
      var error = MAPPER.readTree(body).path("error");
      String status = error.path("status").asText("");
      String message = error.path("message").asText("");
      if (message.length() > 160) {
        message = message.substring(0, 160) + "…";
      }
      String detail = (status + " " + message).trim();
      return detail.isEmpty() ? "" : " — " + detail;
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }

  static String requestBody(String systemPrompt, String userPrompt) {
    ObjectNode body = MAPPER.createObjectNode();
    body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
    ObjectNode content = body.putArray("contents").addObject();
    content.put("role", "user");
    content.putArray("parts").addObject().put("text", userPrompt);
    ObjectNode config = body.putObject("generationConfig");
    config.put("responseMimeType", "application/json");
    config.put("temperature", 0.2);
    config.set("responseSchema", reportSchema());
    return body.toString();
  }

  private static ObjectNode reportSchema() {
    ObjectNode schema = MAPPER.createObjectNode().put("type", "OBJECT");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("summary").put("type", "STRING");
    stringArray(properties.putObject("evidence"));
    ObjectNode suspect = properties.putObject("suspects").put("type", "ARRAY").putObject("items").put("type", "OBJECT");
    ObjectNode suspectProperties = suspect.putObject("properties");
    suspectProperties.putObject("file").put("type", "STRING");
    suspectProperties.putObject("line").put("type", "INTEGER");
    suspectProperties.putObject("reason").put("type", "STRING");
    suspect.putArray("required").add("file").add("line").add("reason");
    stringArray(properties.putObject("actions"));
    ArrayNode levels = properties.putObject("confidence").put("type", "STRING").putArray("enum");
    levels.add("HIGH").add("MEDIUM").add("LOW");
    schema.putArray("required").add("summary").add("evidence").add("suspects").add("actions").add("confidence");
    return schema;
  }

  private static void stringArray(ObjectNode node) {
    node.put("type", "ARRAY").putObject("items").put("type", "STRING");
  }
}
