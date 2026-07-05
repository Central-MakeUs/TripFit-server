package com.tripfit.tripfit.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
		@NotBlank String refreshToken
) {
}
