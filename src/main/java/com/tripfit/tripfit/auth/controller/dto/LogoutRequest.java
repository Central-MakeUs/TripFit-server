package com.tripfit.tripfit.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
		@NotBlank String refreshToken
) {
}
