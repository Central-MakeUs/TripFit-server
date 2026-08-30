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

  // 예외를 로깅할 때 메시지에 담긴 PII가 그대로 새지 않도록, 스택트레이스는 보존하면서 메시지만 마스킹한 대역으로 치환한다.
  // 구조화 로그 변환기는 throwable.getClass()/getMessage() 원시 필드를 그대로 읽으므로(toString() 미사용) 원본 타입명은
  // 메시지 안에 함께 담는다. cause 체인도 재귀적으로 동일 처리
  public static Throwable maskThrowable(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable cause = throwable.getCause();
    Throwable maskedCause = cause == throwable ? null : maskThrowable(cause);
    String label = throwable.getClass().getSimpleName();
    String maskedMessage =
        throwable.getMessage() == null ? label : label + ": " + mask(throwable.getMessage());
    MaskedThrowable masked = new MaskedThrowable(maskedMessage, maskedCause);
    masked.setStackTrace(throwable.getStackTrace());
    return masked;
  }

  // 로그 출력 전용 대역 — 메시지는 이미 마스킹·타입명 포함 상태로 전달받는다
  private static final class MaskedThrowable extends Throwable {

    MaskedThrowable(String maskedMessage, Throwable cause) {
      super(maskedMessage, cause, false, true);
    }
  }
}
