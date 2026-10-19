package com.tripfit.reporter.mask;

import java.util.List;
import java.util.regex.Pattern;

// Gemini 무료 등급은 보낸 내용이 Google 모델 개선에 쓰일 수 있어서, 보내기 전에 개인을 식별할 수 있는 값을 가린다.
// JWT를 Bearer보다 먼저 치환해야 "Bearer eyJ..."가 <token>이 아니라 <jwt>로 남아 무엇이었는지 알 수 있다.
public final class PiiMasker {

  private record Rule(Pattern pattern, String replacement) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), "<jwt>"),
          new Rule(Pattern.compile("(?i)(bearer\\s+)(?!<jwt>)[A-Za-z0-9._~+/=-]+"), "$1<token>"),
          // 쿠키·쿼리에 실린 토큰 값(refreshToken=… 등)
          new Rule(
              Pattern.compile("(?i)\\b(refresh_?token|access_?token|id_?token|jsessionid|session|code)=(?!<)[^;\\s,&\"']+"),
              "$1=<cookie>"),
          new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>"),
          new Rule(
              Pattern.compile(
                  "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
              "<uuid>"),
          new Rule(Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "<ip>"),
          // 휴대전화 번호(010-1234-5678, 01012345678)
          new Rule(Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b"), "<phone>"),
          // FCM 기기 토큰·소셜 토큰처럼 숫자와 영문이 섞인 40자 넘는 덩어리. 숫자가 없는 긴 클래스 이름은 남긴다.
          new Rule(
              Pattern.compile("(?<![\\w:/-])(?=[A-Za-z0-9_:-]*\\d)(?=[A-Za-z0-9_:-]*[A-Za-z])[A-Za-z0-9_:-]{40,}(?![\\w:-])"),
              "<token>"));

  private PiiMasker() {}

  public static String mask(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String masked = text;
    for (Rule rule : RULES) {
      masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
    }
    return masked;
  }
}
