package com.tripfit.tripfit.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// provider 에러 바디 등 외부 응답 문자열에서 이메일을 마스킹 — 로그·last_sync_error 컬럼 저장 전 공통으로 거친다
public final class PiiMasker {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private PiiMasker() {}

  public static String mask(String text) {
    if (text == null) {
      return null;
    }
    Matcher matcher = EMAIL_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement(maskEmail(matcher.group())));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String maskEmail(String email) {
    int at = email.indexOf('@');
    String local = email.substring(0, at);
    String domain = email.substring(at);
    String visible = local.length() <= 2 ? local : local.substring(0, 2);
    return visible + "***" + domain;
  }
}
