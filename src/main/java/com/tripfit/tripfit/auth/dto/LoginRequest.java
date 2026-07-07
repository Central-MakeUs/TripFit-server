package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청")
public record LoginRequest(
		@Schema(description = "소셜 로그인 제공자", example = "GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull SocialProvider provider,
		@Schema(description = "소셜 토큰. GOOGLE/APPLE: id_token, KAKAO: access_token", example = "eyJhbG...", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String token
) {
}
