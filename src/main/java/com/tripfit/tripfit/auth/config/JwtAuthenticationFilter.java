package com.tripfit.tripfit.auth.config;

import com.tripfit.tripfit.auth.client.TokenRevocationChecker;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.service.AccessTokenClaims;
import com.tripfit.tripfit.auth.service.JwtService;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  private final TokenRevocationChecker tokenRevocationChecker;

  private final AuthErrorResponseWriter authErrorResponseWriter;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      TokenRevocationChecker tokenRevocationChecker,
      AuthErrorResponseWriter authErrorResponseWriter) {
    this.jwtService = jwtService;
    this.tokenRevocationChecker = tokenRevocationChecker;
    this.authErrorResponseWriter = authErrorResponseWriter;
  }

  @Override
  // Authorization Bearer 헤더가 있으면 JWT를 검증하고 SecurityContext에 userId를 설정함
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
    if (accessToken.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      AccessTokenClaims claims = jwtService.parseAccessToken(accessToken);
      if (tokenRevocationChecker.isRevoked(claims.jti())) {
        authErrorResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN);
        return;
      }
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(claims.userId()));
      filterChain.doFilter(request, response);
    } catch (TripFitException exception) {
      // JWT 파싱·검증 실패 시 envelope 형식의 401 응답을 반환함
      ErrorCode errorCode = exception.getErrorCode();
      authErrorResponseWriter.write(response, errorCode);
    }
  }
}
