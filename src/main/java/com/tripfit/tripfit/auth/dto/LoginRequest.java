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
        description = "APPLE 전용 authorization code — provider가 APPLE이면 필수, GOOGLE/KAKAO는 보내지 않음(값이 있어도 무시). Apple 네이티브 Sign in 인증 결과에 포함된 authorizationCode 원문. 탈퇴 시 Apple 쪽 연결을 해제(revoke)하는 데 쓰일 refresh token을 교환하기 위한 값",
        example = "c1234...",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String authorizationCode
) {
}
