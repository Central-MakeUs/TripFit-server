package com.tripfit.tripfit.auth.oauth;

import java.util.Locale;

public final class SocialErrorMessages {

  private SocialErrorMessages() {}

  public static boolean containsExpired(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains("expired");
  }
}
