package com.tripfit.reporter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeminiResponseParserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // generateContent 응답 껍데기에 모델이 쓴 텍스트를 넣는다.
  private static String response(String modelText) {
    var root = MAPPER.createObjectNode();
    root.putArray("candidates").addObject().putObject("content").putArray("parts").addObject().put("text", modelText);
    return root.toString();
  }

  private static final String REPORT =
      """
      {"summary": "여행방 목록 조회가 몰려 DB 커넥션이 바닥났습니다.",
       "evidence": ["DB 커넥션 대기 최대 12", ""],
       "suspects": [{"file": "src/main/java/A.java", "line": 42, "reason": "N+1 조회"}, {"file": "", "line": 1, "reason": "x"}],
       "actions": ["목록 조회에 페이지 크기 상한을 둔다"],
       "confidence": "medium"}
      """;

  @Test
  void parse_validReport_readsFieldsAndDropsBlankItems() throws Exception {
    IncidentReport report = GeminiResponseParser.parse(response(REPORT));

    assertThat(report.summary()).startsWith("여행방 목록 조회");
    assertThat(report.evidence()).containsExactly("DB 커넥션 대기 최대 12");
    assertThat(report.suspects()).containsExactly(new IncidentReport.Suspect("src/main/java/A.java", 42, "N+1 조회"));
    assertThat(report.confidence()).isEqualTo(IncidentReport.Confidence.MEDIUM);
  }

  @Test
  void parse_reportWrappedInCodeFence_unwraps() throws Exception {
    IncidentReport report = GeminiResponseParser.parse(response("```json\n" + REPORT + "\n```"));

    assertThat(report.actions()).hasSize(1);
  }

  @Test
  void parse_noCandidates_throwsWithFinishReason() {
    String blocked = "{\"candidates\": [{\"finishReason\": \"SAFETY\"}]}";

    assertThatThrownBy(() -> GeminiResponseParser.parse(blocked))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("SAFETY");
  }

  @Test
  void parse_modelTextNotJson_throws() {
    assertThatThrownBy(() -> GeminiResponseParser.parse(response("원인을 모르겠습니다")))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("JSON");
  }

  @Test
  void parse_unknownConfidence_throws() {
    String bad = REPORT.replace("\"medium\"", "\"VERY_HIGH\"");

    assertThatThrownBy(() -> GeminiResponseParser.parse(response(bad)))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("confidence");
  }

  @Test
  void parse_missingSummary_throws() {
    String bad = REPORT.replace("\"summary\": \"여행방 목록 조회가 몰려 DB 커넥션이 바닥났습니다.\"", "\"summary\": \"\"");

    assertThatThrownBy(() -> GeminiResponseParser.parse(response(bad)))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("summary");
  }

  @Test
  void requestBody_keepsApiKeyOutOfBodyAndAsksForJson() throws Exception {
    var body = MAPPER.readTree(GeminiClient.requestBody("시스템", "사용자"));

    assertThat(body.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
    assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo("시스템");
    assertThat(body.toString()).doesNotContain("key");
  }
}
