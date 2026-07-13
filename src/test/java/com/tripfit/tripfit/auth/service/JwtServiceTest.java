package com.tripfit.tripfit.auth.service;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.config.JwtProperties;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setSecret("test-jwt-secret-key-at-least-32-characters");
    jwtProperties.setAccessExpirationSeconds(7200);
    jwtService = new JwtService(jwtProperties);
  }

  @Test
  void createAndParseAccessToken() {
    String token =
        jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
    UUID userId = jwtService.parseUserId(token);
    assertThat(userId).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
  }

  @Test
  void parseAccessToken_returnsUserIdAndJti() {
    String token =
        jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
    AccessTokenClaims claims = jwtService.parseAccessToken(token);
    assertThat(claims.userId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
    assertThat(claims.jti()).isNotBlank();
  }

  @Test
  void parseInvalidToken_throws() {
    assertThatThrownBy(() -> jwtService.parseUserId("invalid-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN);
  }
}
