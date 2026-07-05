package com.tripfit.tripfit.auth.controller.dto;

public record RefreshResponse(
		String accessToken,
		long expiresIn
) {
}
