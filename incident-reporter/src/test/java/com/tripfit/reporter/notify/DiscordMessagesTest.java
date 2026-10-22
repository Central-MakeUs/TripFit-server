package com.tripfit.reporter.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.llm.IncidentReport;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordMessagesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Alert ALERT =
      new Alert("firing", Map.of("alertname", "DB 커넥션 풀 대기", "resource", "db_pool", "target", "db"),
          null, "2026-10-19T09:00:00Z", null, "f1", null, Map.of("A", 3.0));

  private static JsonNode embed(String json) throws Exception {
    return MAPPER.readTree(json).path("embeds").path(0);
  }

  @Test
  void firing_includesAllSectionsAndEnvironmentTitle() throws Exception {
    IncidentReport report =
        new IncidentReport("커넥션이 바닥났습니다.", List.of("대기 최대 12"),
            List.of(new IncidentReport.Suspect("src/A.java", 7, "긴 트랜잭션")), List.of("트랜잭션을 줄인다"),
            IncidentReport.Confidence.HIGH);

    JsonNode embed = embed(DiscordMessages.firing("local", ALERT, report, 2, List.of("로그 조회 실패 (timeout)"),
        URI.create("http://localhost:3000")));

    assertThat(embed.path("title").asText()).isEqualTo("🚨 [local] DB 커넥션 풀 대기");
    assertThat(embed.path("url").asText()).isEqualTo("http://localhost:3000");
    String description = embed.path("description").asText();
    assertThat(description)
        .contains("**무슨 일**", "**근거**", "**의심 코드**", "**제안 조치**", "**확신도** 높음")
        .contains("`src/A.java:7` 긴 트랜잭션")
        .contains("확인 안 된 위치 2개 제외")
        .contains("로그 조회 실패");
  }

  @Test
  void fallback_showsFailureReasonAndAlertValues() throws Exception {
    JsonNode embed = embed(DiscordMessages.fallback("prod", ALERT, "Gemini 호출 한도 초과(429)", null, null));

    assertThat(embed.path("description").asText()).contains("AI 분석 실패:** Gemini 호출 한도 초과(429)", "규칙이 본 값 A: 3.0");
    assertThat(embed.has("url")).isFalse();
  }

  @Test
  void resolved_isSingleLineWithDuration() throws Exception {
    JsonNode body = MAPPER.readTree(DiscordMessages.resolved("prod", ALERT, Duration.ofMinutes(75)));

    assertThat(body.path("content").asText()).isEqualTo("✅ [prod] DB 커넥션 풀 대기 해제 (1시간 15분)");
    assertThat(body.has("embeds")).isFalse();
  }

  @Test
  void firing_longDescription_cutToDiscordLimit() throws Exception {
    IncidentReport report =
        new IncidentReport("가".repeat(5000), List.of(), List.of(), List.of(), IncidentReport.Confidence.LOW);

    String description = embed(DiscordMessages.firing("local", ALERT, report, 0, List.of(), null))
        .path("description").asText();

    assertThat(description).hasSize(DiscordMessages.DESCRIPTION_LIMIT).endsWith("…");
  }

  @Test
  void truncate_exactlyAtLimit_unchanged() {
    String text = "a".repeat(DiscordMessages.DESCRIPTION_LIMIT);

    assertThat(DiscordMessages.truncate(text, DiscordMessages.DESCRIPTION_LIMIT)).isEqualTo(text);
    assertThat(DiscordMessages.truncate(text + "b", DiscordMessages.DESCRIPTION_LIMIT))
        .hasSize(DiscordMessages.DESCRIPTION_LIMIT);
  }

  @Test
  void humanize_underOneMinute() {
    assertThat(DiscordMessages.humanize(Duration.ofSeconds(20))).isEqualTo("1분 미만");
  }
}
