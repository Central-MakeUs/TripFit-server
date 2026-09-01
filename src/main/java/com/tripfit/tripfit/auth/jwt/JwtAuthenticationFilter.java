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

  // 로그인, 토큰 재발급 등 인증이 필요 없거나 토큰이 아직 없는 공개 엔드포인트는
  // JWT 검증 필터를 거치지 않고 바로 통과시킵니다.
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && PUBLIC_AUTH_POST_PATHS.contains(request.getRequestURI());
  }

  // 매 API 요청마다 헤더의 JWT 토큰을 추출하고 검증하여 SecurityContext에 인증 정보를 등록합니다.
  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {
    // 1. Authorization 헤더 존재 여부 및 Bearer 타입 확인
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 2. 토큰 값 추출 및 공백 체크
    String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
    if (accessToken.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      // 3. 서명 검증 및 페이로드(Claims) 파싱
      AccessTokenClaims claims = jwtService.parseAccessToken(accessToken);
      // 4. 검증 성공 시 Spring Security Context에 User ID 등록 (이후 Controller에서 사용)
      SecurityContextHolder.getContext()
          .setAuthentication(new JwtAuthentication(claims.userId()));
      filterChain.doFilter(request, response);
    } catch (TripFitException exception) {
      // 5. 토큰 만료 또는 변조된 경우 커스텀 에러 응답을 작성하여 반환
      ErrorCode errorCode = exception.getErrorCode();
      authErrorResponseWriter.write(response, errorCode);
    }
  }
}
