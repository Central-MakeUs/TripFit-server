package com.tripfit.tripfit.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

// SocialLogContext 필드를 MDC에 채운 뒤 로그를 남기고 정리 — auth.oauth/auth.service/user.googlecalendar
// 패키지 전용 JSON appender(logback-spring.xml)가 이 MDC 값을 그대로 구조화 필드로 직렬화한다
public final class SocialIntegrationLog {

  private SocialIntegrationLog() {}

  public static void warn(
      Logger logger,
      SocialLogContext context,
      String message,
      Throwable throwable) {
    withMdc(context, () -> logger.warn(message, maskMessages(throwable)));
  }

  public static void warn(Logger logger, SocialLogContext context, String message) {
    withMdc(context, () -> logger.warn(message));
  }

  public static void error(
      Logger logger,
      SocialLogContext context,
      String message,
      Throwable throwable) {
    withMdc(context, () -> logger.error(message, maskMessages(throwable)));
  }

  public static void info(Logger logger, SocialLogContext context, String message) {
    withMdc(context, () -> logger.info(message));
  }

  private static void withMdc(SocialLogContext context, Runnable logCall) {
    Map<String, String> fields = toMdcFields(context);
    fields.forEach(MDC::put);
    try {
      logCall.run();
    } finally {
      fields.keySet().forEach(MDC::remove);
    }
  }

  private static Map<String, String> toMdcFields(SocialLogContext context) {
    Map<String, String> fields = new LinkedHashMap<>();
    putIfPresent(fields, "provider", context.provider() == null ? null : context.provider().name());
    putIfPresent(fields, "action", context.action() == null ? null : context.action().name());
    putIfPresent(fields, "userId", context.userId() == null ? null : context.userId().toString());
    putIfPresent(
        fields,
        "httpStatus",
        context.httpStatus() == null ? null : context.httpStatus().toString());
    putIfPresent(fields, "providerErrorReason", context.providerErrorReason());
    putIfPresent(fields, "providerErrorMessage", context.providerErrorMessage());
    putIfPresent(fields, "trigger", context.trigger());
    putIfPresent(fields, "grantedScope", context.grantedScope());
    return fields;
  }

  private static void putIfPresent(Map<String, String> fields, String key, String value) {
    if (value != null) {
      fields.put(key, value);
    }
  }

  // provider 예외 메시지에 담긴 이메일 등 PII가 구조화 로그(Loki)로 그대로 나가지 않도록, 스택트레이스는 보존하면서
  // 메시지만 PiiMasker로 감싼 대역으로 치환한다 — logback 구조화 변환기는 throwable.getClass()/getMessage() 원시
  // 필드를 그대로 읽으므로(toString() 미사용) 원본 타입명은 메시지 안에 함께 담는다. cause 체인도 재귀적으로 동일 처리
  private static Throwable maskMessages(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable cause = throwable.getCause();
    Throwable maskedCause = cause == throwable ? null : maskMessages(cause);
    String label = throwable.getClass().getSimpleName();
    String maskedMessage =
        throwable.getMessage() == null
            ? label
            : label + ": " + PiiMasker.mask(throwable.getMessage());
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
