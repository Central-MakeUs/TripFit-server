package com.tripfit.tripfit.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripfit.tripfit.auth.client.TokenRevocationChecker;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.service.JwtService;
import com.tripfit.tripfit.common.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock
  private TokenRevocationChecker tokenRevocationChecker;

  @Mock
  private FilterChain filterChain;

  private JwtService jwtService;

  private JwtAuthenticationFilter filter;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setSecret("test-jwt-secret-key-at-least-32-characters");
    jwtProperties.setAccessExpirationSeconds(3600);
    jwtService = new JwtService(jwtProperties);
    objectMapper = new ObjectMapper();
    filter = new JwtAuthenticationFilter(
        jwtService,
        tokenRevocationChecker,
        new AuthErrorResponseWriter());
  }

  @Test
  void doFilterInternal_validToken_setsSecurityContext() throws Exception {
    String token = jwtService.createAccessToken(7L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(tokenRevocationChecker.isRevoked(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isInstanceOf(JwtAuthentication.class);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
  }

  @Test
  void doFilterInternal_revokedToken_returns401() throws Exception {
    String token = jwtService.createAccessToken(7L);
    String jti = jwtService.parseAccessToken(token).jti();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(tokenRevocationChecker.isRevoked(jti)).thenReturn(true);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(401);
    ErrorResponse errorResponse =
        objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
    assertThat(errorResponse.code()).isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN.getCode());
  }

  @Test
  void doFilterInternal_invalidToken_returns401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(401);
    ErrorResponse errorResponse =
        objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
    assertThat(errorResponse.code()).isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN.getCode());
  }

  @Test
  void doFilterInternal_withoutAuthorizationHeader_continuesChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
