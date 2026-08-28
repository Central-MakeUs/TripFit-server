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

  @Override

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && PUBLIC_AUTH_POST_PATHS.contains(request.getRequestURI());
  }

  @Override

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

      ErrorCode errorCode = exception.getErrorCode();
      authErrorResponseWriter.write(response, errorCode);
    }
  }
}
