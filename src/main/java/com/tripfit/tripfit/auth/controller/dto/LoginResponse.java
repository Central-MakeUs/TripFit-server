package com.tripfit.tripfit.auth.controller.dto;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		long expiresIn,
		UserSummaryResponse user
) {
}
