package com.tripfit.reporter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Gemini generateContent 응답(candidates[].content.parts[].text)에서 리포트 JSON을 꺼내 검증한다.
// 형식이 하나라도 어긋나면 리포트로 쓰지 않고 LlmException을 던져, 호출한 쪽이 수치만 담은 메시지로 대신 보내게 한다.
final class GeminiResponseParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GeminiResponseParser() {}

  static IncidentReport parse(String responseBody) throws LlmException {
    JsonNode root = readTree(responseBody, "Gemini 응답이 JSON이 아닙니다");
    JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
    StringBuilder text = new StringBuilder();
    for (JsonNode part : parts) {
      text.append(part.path("text").asText(""));
    }
    if (text.isEmpty()) {
      String reason = root.path("candidates").path(0).path("finishReason").asText("");
      throw new LlmException("Gemini 응답에 내용이 없습니다" + (reason.isEmpty() ? "" : "(" + reason + ")"));
    }

    JsonNode report = readTree(stripCodeFence(text.toString()), "리포트 JSON 형식이 아닙니다");
    String summary = report.path("summary").asText("").trim();
    if (summary.isEmpty()) {
      throw new LlmException("리포트에 summary가 없습니다");
    }
    IncidentReport.Confidence confidence;
    try {
      confidence = IncidentReport.Confidence.valueOf(report.path("confidence").asText("").trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new LlmException("리포트의 confidence 값이 올바르지 않습니다");
    }

    List<IncidentReport.Suspect> suspects = new ArrayList<>();
    for (JsonNode suspect : report.path("suspects")) {
      String file = suspect.path("file").asText("").trim();
      int line = suspect.path("line").asInt(0);
      if (!file.isEmpty()) {
        suspects.add(new IncidentReport.Suspect(file, line, suspect.path("reason").asText("").trim()));
      }
    }
    return new IncidentReport(summary, texts(report.path("evidence")), suspects, texts(report.path("actions")), confidence);
  }

  private static List<String> texts(JsonNode array) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : array) {
      String value = item.asText("").trim();
      if (!value.isEmpty()) {
        values.add(value);
      }
    }
    return values;
  }

  // JSON 응답 형식을 지정해도 모델이 ```json 코드 블록으로 감싸 보내는 경우가 있어 벗겨 낸다.
  private static String stripCodeFence(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
    }
    return trimmed;
  }

  private static JsonNode readTree(String json, String error) throws LlmException {
    try {
      JsonNode node = MAPPER.readTree(json);
      if (node == null || !node.isObject()) {
        throw new LlmException(error);
      }
      return node;
    } catch (IOException e) {
      throw new LlmException(error, e);
    }
  }
}
