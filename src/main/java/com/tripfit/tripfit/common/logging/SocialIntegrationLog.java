package com.tripfit.tripfit.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

public final class SocialIntegrationLog {

  private SocialIntegrationLog() {}

  public static void warn(
      Logger logger,
      SocialLogContext context,
      String message,
      Throwable throwable) {
    withMdc(context, () -> logger.warn(message, PiiMasker.maskThrowable(throwable)));
  }

  public static void warn(Logger logger, SocialLogContext context, String message) {
    withMdc(context, () -> logger.warn(message));
  }

  public static void error(
      Logger logger,
      SocialLogContext context,
      String message,
      Throwable throwable) {
    withMdc(context, () -> logger.error(message, PiiMasker.maskThrowable(throwable)));
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
