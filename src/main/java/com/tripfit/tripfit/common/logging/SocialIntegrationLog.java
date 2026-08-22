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
    withMdc(context, () -> logger.warn(message, throwable));
  }

  public static void warn(Logger logger, SocialLogContext context, String message) {
    withMdc(context, () -> logger.warn(message));
  }

  public static void error(
      Logger logger,
      SocialLogContext context,
      String message,
      Throwable throwable) {
    withMdc(context, () -> logger.error(message, throwable));
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
}
