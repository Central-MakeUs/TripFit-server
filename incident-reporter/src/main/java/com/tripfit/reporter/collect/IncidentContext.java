package com.tripfit.reporter.collect;

import java.util.List;

// 리포터가 모은 자료 전체다. 모으다 실패한 항목은 missing에 사유를 남겨, 리포트에 "빠진 자료"로 표시한다.
public record IncidentContext(
    List<MetricSummary> metrics,
    List<EndpointStat> endpoints,
    List<String> logLines,
    List<SourceCodeLocator.Snippet> snippets,
    List<String> missing) {

  // 수치 하나를 구간의 최소·최대·마지막 값으로 줄인 것이다. 시계열 전체를 보내면 프롬프트가 너무 길어진다.
  public record MetricSummary(String name, String description, double min, double max, double last) {}

  // 요청이 많은 API 하나의 초당 요청 수와 p95 응답 시간(초)이다. p95는 히스토그램이 없으면 NaN이다.
  public record EndpointStat(String method, String uri, double requestsPerSecond, double p95Seconds) {}

  public IncidentContext {
    metrics = List.copyOf(metrics);
    endpoints = List.copyOf(endpoints);
    logLines = List.copyOf(logLines);
    snippets = List.copyOf(snippets);
    missing = List.copyOf(missing);
  }
}
