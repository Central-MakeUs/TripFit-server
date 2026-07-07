package com.tripfit.tripfit.auth.dto;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		long expiresIn,
		UserSummaryResponse user
) {
}
