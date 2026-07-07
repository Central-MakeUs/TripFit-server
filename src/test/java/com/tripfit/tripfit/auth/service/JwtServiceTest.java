package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.config.JwtProperties;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.service.AccessTokenClaims;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
		String token = jwtService.createAccessToken(42L);
		Long userId = jwtService.parseUserId(token);
		assertThat(userId).isEqualTo(42L);
	}

	@Test
	void parseAccessToken_returnsUserIdAndJti() {
		String token = jwtService.createAccessToken(42L);
		AccessTokenClaims claims = jwtService.parseAccessToken(token);
		assertThat(claims.userId()).isEqualTo(42L);
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
