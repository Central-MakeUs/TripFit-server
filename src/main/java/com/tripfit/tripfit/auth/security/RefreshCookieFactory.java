package com.tripfit.tripfit.auth.security;

import com.tripfit.tripfit.auth.config.AuthCookieProperties;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

  private static final String COOKIE_NAME = "refreshToken";

  private static final String COOKIE_PATH = "/api/v1/auth";

  private final AuthCookieProperties cookieProperties;

  private final JwtProperties jwtProperties;

  public ResponseCookie issue(String token) {
    return baseCookie(token).maxAge(Duration.ofDays(jwtProperties.getRefreshExpirationDays()))
        .build();
  }

  public ResponseCookie clear() {
    return baseCookie("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(cookieProperties.isSecure())
        .sameSite("Lax")
        .domain(cookieProperties.getDomain())
        .path(COOKIE_PATH);
  }
}
