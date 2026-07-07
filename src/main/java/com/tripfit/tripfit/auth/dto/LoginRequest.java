package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.domain.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
		@NotNull SocialProvider provider,
		@NotBlank String token
) {
}
