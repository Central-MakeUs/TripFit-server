package com.tripfit.tripfit.auth.dto;

public record RefreshResponse(
		String accessToken,
		long expiresIn
) {
}
