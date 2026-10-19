package com.tripfit.reporter.llm;

import java.util.List;

// 모델이 돌려준 원인 리포트다. 필드 구성은 docs/specs/cross-cutting/incident-ai-report.md "분석"이 정한다.
public record IncidentReport(
    String summary,
    List<String> evidence,
    List<Suspect> suspects,
    List<String> actions,
    Confidence confidence) {

  public record Suspect(String file, int line, String reason) {}

  public enum Confidence {
    HIGH("높음"),
    MEDIUM("중간"),
    LOW("낮음");

    private final String label;

    Confidence(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  public IncidentReport {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    suspects = suspects == null ? List.of() : List.copyOf(suspects);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }

  public IncidentReport withSuspects(List<Suspect> kept) {
    return new IncidentReport(summary, evidence, kept, actions, confidence);
  }
}
