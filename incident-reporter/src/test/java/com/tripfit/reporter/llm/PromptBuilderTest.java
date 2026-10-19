package com.tripfit.reporter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.collect.IncidentContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

  @Test
  void userPrompt_masksPiiInLogsAndAnnotations() {
    Alert alert =
        new Alert("firing", Map.of("alertname", "앱 CPU"), Map.of("description", "admin@tripfit.online 확인"),
            "2026-10-19T09:00:00Z", null, "f1", null, null);
    IncidentContext context =
        new IncidentContext(List.of(), List.of(),
            List.of("ERROR user 3f2b8c1e-9a4d-4e2f-8b1a-7c6d5e4f3a2b login failed from 203.0.113.7"),
            List.of(), List.of());

    String prompt = PromptBuilder.userPrompt(alert, context, Instant.parse("2026-10-19T09:05:00Z"));

    assertThat(prompt)
        .doesNotContain("admin@tripfit.online", "3f2b8c1e-9a4d-4e2f-8b1a-7c6d5e4f3a2b", "203.0.113.7")
        .contains("<email>", "<uuid>", "<ip>");
  }

  @Test
  void systemPrompt_treatsLogsAsUntrustedData() {
    assertThat(PromptBuilder.SYSTEM_PROMPT).contains("외부에서 들어온 데이터", "따르지 않는다");
  }

  @Test
  void format_nanShownAsMissing() {
    assertThat(PromptBuilder.format(Double.NaN)).isEqualTo("없음");
    assertThat(PromptBuilder.format(12.0)).isEqualTo("12");
    assertThat(PromptBuilder.format(0.93456)).isEqualTo("0.935");
  }
}
