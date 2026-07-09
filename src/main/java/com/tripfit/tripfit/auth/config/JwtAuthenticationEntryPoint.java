package com.tripfit.tripfit.auth.config;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final AuthErrorResponseWriter authErrorResponseWriter;

  public JwtAuthenticationEntryPoint(AuthErrorResponseWriter authErrorResponseWriter) {
    this.authErrorResponseWriter = authErrorResponseWriter;
  }

  @Override
  // 인증되지 않은 요청이 보호 API에 접근할 때 JSON 401 envelope을 반환함
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    authErrorResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN);
  }
}
