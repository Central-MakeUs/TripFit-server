package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청. POST /auth/login")
public record LoginRequest(
    @Schema(
        description = "소셜 로그인 제공자",
        example = "GOOGLE",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull SocialProvider provider,

    @Schema(
        description = "소셜 토큰. GOOGLE/APPLE: id_token, KAKAO: access_token",
        example = "eyJhbG...",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String token,

    @Schema(
        description = "APPLE 전용 authorization code. 탈퇴 시 Apple revoke 호출(#64)에 필요한 refresh token 교환용 — GOOGLE/KAKAO는 보내지 않음",
        example = "c1234...",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String authorizationCode
) {
}
