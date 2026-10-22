package com.tripfit.reporter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripfit.reporter.alert.Alert;
import com.tripfit.reporter.collect.IncidentContext;
import com.tripfit.reporter.llm.IncidentReport;
import com.tripfit.reporter.mask.PiiMasker;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Discord 웹훅으로 보낼 메시지 본문(JSON)을 만든다. 장애 한 건에 발생 리포트 하나와 해제 한 줄이 가고,
// AI 분석이 실패하면 리포트 대신 알림 내용과 핵심 수치만 담은 메시지가 간다.
public final class DiscordMessages {

  static final int TITLE_LIMIT = 256;
  static final int DESCRIPTION_LIMIT = 4096;
  private static final int COLOR_FIRING = 0xE53935;
  private static final int COLOR_FALLBACK = 0xFB8C00;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DiscordMessages() {}

  public static String firing(
      String env, Alert alert, IncidentReport report, int droppedSuspects, List<String> missing, URI link) {
    StringBuilder d = new StringBuilder();
    section(d, "무슨 일", report.summary());
    bullets(d, "근거", report.evidence());
    d.append("**의심 코드**\n");
    if (report.suspects().isEmpty()) {
      d.append("- 짚을 수 있는 코드 위치가 없습니다\n");
    }
    for (IncidentReport.Suspect suspect : report.suspects()) {
      d.append("- `").append(suspect.file()).append(':').append(suspect.line()).append("` ").append(suspect.reason()).append('\n');
    }
    if (droppedSuspects > 0) {
      d.append("- (확인 안 된 위치 ").append(droppedSuspects).append("개 제외)\n");
    }
    d.append('\n');
    bullets(d, "제안 조치", report.actions());
    d.append("**확신도** ").append(report.confidence().label()).append('\n');
    if (!missing.isEmpty()) {
      // 사유에는 예외 메시지(내부 주소 등)가 섞일 수 있어 같은 규칙으로 가린다.
      d.append("\n**빠진 자료** ").append(PiiMasker.mask(String.join(" · ", missing))).append('\n');
    }
    return embed("🚨 [" + env + "] " + alert.ruleName(), d.toString(), COLOR_FIRING, link);
  }

  public static String fallback(String env, Alert alert, String reason, IncidentContext context, URI link) {
    StringBuilder d = new StringBuilder();
    d.append("**AI 분석 실패:** ").append(reason).append("\n\n");
    d.append("**알림**\n- 대상: ").append(alert.target()).append(" / 자원: ").append(alert.resource()).append('\n');
    for (Map.Entry<String, Double> value : alert.values().entrySet()) {
      d.append("- 규칙이 본 값 ").append(value.getKey()).append(": ").append(value.getValue()).append('\n');
    }
    if (context != null && !context.metrics().isEmpty()) {
      d.append("\n**핵심 수치** (최소 / 최대 / 마지막)\n");
      for (IncidentContext.MetricSummary metric : context.metrics()) {
        d.append("- ").append(metric.description()).append(": ").append(metric.min()).append(" / ")
            .append(metric.max()).append(" / ").append(metric.last()).append('\n');
      }
    }
    if (context != null && !context.endpoints().isEmpty()) {
      d.append("\n**요청 많은 API**\n");
      for (IncidentContext.EndpointStat endpoint : context.endpoints()) {
        d.append("- ").append(endpoint.method()).append(' ').append(endpoint.uri()).append(": 초당 ")
            .append(String.format("%.1f", endpoint.requestsPerSecond())).append("건\n");
      }
    }
    return embed("🚨 [" + env + "] " + alert.ruleName(), d.toString(), COLOR_FALLBACK, link);
  }

  // 메시지를 만들다 예외가 났을 때 보내는 최소 문구다. 여기서는 더 실패할 일이 없도록 아무것도 조회하지 않는다.
  public static String minimal(String env, Alert alert, String reason) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("content", truncate("🚨 [" + env + "] " + alert.ruleName() + " — " + reason, 2000));
    noMentions(body);
    return body.toString();
  }

  public static String resolved(String env, Alert alert, Duration duration) {
    ObjectNode body = MAPPER.createObjectNode();
    noMentions(body);
    body.put("content", truncate("✅ [" + env + "] " + alert.ruleName() + " 해제 (" + humanize(duration) + ")", 2000));
    return body.toString();
  }

  static String humanize(Duration duration) {
    long minutes = Math.max(0, duration.toMinutes());
    if (minutes < 1) {
      return "1분 미만";
    }
    return minutes < 60 ? minutes + "분" : (minutes / 60) + "시간 " + (minutes % 60) + "분";
  }

  // 이모지 같은 두 칸짜리 문자(서로게이트 쌍)를 반으로 자르지 않도록 경계를 한 칸 당긴다.
  static String truncate(String text, int limit) {
    if (text.length() <= limit) {
      return text;
    }
    int end = limit - 1;
    if (Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end) + "…";
  }

  // 로그·규칙 이름에 @everyone 같은 문자열이 섞여도 채널 전체 알림이 울리지 않게 한다.
  private static void noMentions(ObjectNode body) {
    body.putObject("allowed_mentions").putArray("parse");
  }

  private static String embed(String title, String description, int color, URI link) {
    ObjectNode body = MAPPER.createObjectNode();
    noMentions(body);
    ObjectNode embed = body.putArray("embeds").addObject();
    embed.put("title", truncate(title, TITLE_LIMIT));
    embed.put("description", truncate(description, DESCRIPTION_LIMIT));
    embed.put("color", color);
    if (link != null) {
      embed.put("url", link.toString());
    }
    return body.toString();
  }

  private static void section(StringBuilder d, String title, String text) {
    d.append("**").append(title).append("**\n").append(text).append("\n\n");
  }

  private static void bullets(StringBuilder d, String title, List<String> items) {
    d.append("**").append(title).append("**\n");
    if (items.isEmpty()) {
      d.append("- 없음\n");
    }
    items.forEach(item -> d.append("- ").append(item).append('\n'));
    d.append('\n');
  }
}
