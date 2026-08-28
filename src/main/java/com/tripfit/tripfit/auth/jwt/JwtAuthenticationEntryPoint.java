package com.tripfit.tripfit.auth.jwt;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.security.AuthErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final AuthErrorResponseWriter authErrorResponseWriter;

  public JwtAuthenticationEntryPoint(AuthErrorResponseWriter authErrorResponseWriter) {
    this.authErrorResponseWriter = authErrorResponseWriter;
  }

  @Override

  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    authErrorResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN);
  }
}
