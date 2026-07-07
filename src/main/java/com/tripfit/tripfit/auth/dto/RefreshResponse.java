package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 재발급 성공 응답")
public record RefreshResponse(
		@Schema(description = "새 TripFit access JWT", example = "eyJhbG...")
		String accessToken,
		@Schema(description = "access JWT 만료까지 남은 초", example = "7200")
		long expiresIn
) {
}
