package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "액세스 토큰 재발급 요청. POST /auth/refresh")
public record RefreshRequest(
    @Schema(
        description = "login 또는 이전 refresh 응답에서 받은 refresh token",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String refreshToken
) {
}
