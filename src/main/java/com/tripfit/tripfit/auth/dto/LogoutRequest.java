package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청. POST /auth/logout")
public record LogoutRequest(
    @Schema(
        description = "서버에서 폐기할 refresh token",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String refreshToken,

    @Schema(
        description = "현재 보유한 access token. 있으면 만료 전이라도 즉시 무효화(블랙리스트 등록)한다. 없거나 이미 만료·위조된 값이면 조용히 무시하고 로그아웃 자체는 계속 성공한다",
        example = "eyJhbG...",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true) String accessToken
) {
}
