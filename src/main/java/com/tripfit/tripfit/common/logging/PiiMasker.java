package com.tripfit.tripfit.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static final class MaskedThrowable extends Throwable {

    MaskedThrowable(String maskedMessage, Throwable cause) {
      super(maskedMessage, cause, false, true);
    }
  }
}
