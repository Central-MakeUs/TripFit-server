package com.tripfit.tripfit.auth.jwt;

import com.tripfit.tripfit.auth.security.AuthErrorResponseWriter;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  // permitAll 인증 엔드포인트 SSOT — SecurityConfig의 POST permitAll 등록도 이 목록을 그대로 씀(drift 방지)
  public static final Set<String> PUBLIC_AUTH_POST_PATHS =
      Set.of(
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout",
          "/api/v1/auth/apple/notifications");

  private final JwtService jwtService;

  private final AuthErrorResponseWriter authErrorResponseWriter;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      AuthErrorResponseWriter authErrorResponseWriter) {
    this.jwtService = jwtService;
    this.authErrorResponseWriter = authErrorResponseWriter;
  }

  @Override
  // permitAll 엔드포인트는 인증 파싱 자체를 안 태움 — 태우면 클라이언트가 관성적으로 실어 보내는 만료된
  // 액세스 토큰 때문에 로그인·재발급(refresh) 요청 자체가 컨트롤러 도달 전에 401로 막히는 문제가 생김
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && PUBLIC_AUTH_POST_PATHS.contains(request.getRequestURI());
  }

  @Override
  // 1. Bearer 없거나 빈 토큰 → 익명으로 chain 계속 (authenticated API는 EntryPoint/Resolver가 차단)
  // 2. 파싱 성공 시 SecurityContext에 JwtAuthentication 설정
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
      SecurityContextHolder.getContext()
          .setAuthentication(new JwtAuthentication(claims.userId()));
      filterChain.doFilter(request, response);
    } catch (TripFitException exception) {
      // JWT 파싱·검증 실패 — Filter 경로라 GlobalExceptionHandler 대신 Writer 사용
      ErrorCode errorCode = exception.getErrorCode();
      authErrorResponseWriter.write(response, errorCode);
    }
  }
}
