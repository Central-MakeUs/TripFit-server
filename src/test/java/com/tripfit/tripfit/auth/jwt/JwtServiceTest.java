package com.tripfit.tripfit.auth.jwt;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    jwtProperties.setAccessExpirationSeconds(900);
    jwtService = new JwtService(jwtProperties);
  }

  @Test
  void createAndParseAccessToken() {
    String token =
        jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
    UUID userId = jwtService.parseAccessToken(token).userId();
    assertThat(userId).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440042"));
  }

  @Test
  void parseInvalidToken_throws() {
    assertThatThrownBy(() -> jwtService.parseAccessToken("invalid-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN);
  }
}
