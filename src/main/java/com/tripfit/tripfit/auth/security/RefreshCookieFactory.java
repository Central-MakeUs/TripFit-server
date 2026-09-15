package com.tripfit.tripfit.auth.security;

import com.tripfit.tripfit.auth.config.AuthCookieProperties;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// refreshToken을 HttpOnly 쿠키로 내려주는 login/refresh와, 로그아웃 시 지우는 logout이 공유하는 쿠키 조립기
@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

  private static final String COOKIE_NAME = "refreshToken";

  // refresh·logout 두 경로에만 실리면 되므로 그 외 API 요청에는 쿠키가 딸려가지 않도록 범위를 좁힌다
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
