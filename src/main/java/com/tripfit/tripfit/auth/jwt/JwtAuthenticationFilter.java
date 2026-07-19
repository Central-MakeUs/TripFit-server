package com.tripfit.tripfit.auth.jwt;

import com.tripfit.tripfit.auth.oauth.TokenRevocationChecker;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.security.AuthErrorResponseWriter;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
  // 1. Bearer 없거나 빈 토큰 → 익명으로 chain 계속 (authenticated API는 EntryPoint/Resolver가 차단)
  // 2. 파싱 후 jti 폐기 여부 검사 → SecurityContext에 JwtAuthentication 설정
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {
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
      // logout 등으로 폐기된 access jti는 Advice 전에 Filter에서 401 envelope
      if (tokenRevocationChecker.isRevoked(claims.jti())) {
        authErrorResponseWriter.write(response, AuthErrorCode.AUTH_INVALID_TOKEN);
        return;
      }
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(claims.userId()));
      filterChain.doFilter(request, response);
    } catch (TripFitException exception) {
      // JWT 파싱·검증 실패 — Filter 경로라 GlobalExceptionHandler 대신 Writer 사용
      ErrorCode errorCode = exception.getErrorCode();
      authErrorResponseWriter.write(response, errorCode);
    }
  }
}
