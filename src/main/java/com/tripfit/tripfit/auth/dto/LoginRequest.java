package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청입니다. (POST /auth/login)")
public record LoginRequest(
    @Schema(
        description = "소셜 로그인 제공자",
        example = "GOOGLE",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull SocialProvider provider,

    @Schema(
        description = """
            소셜 액세스 토큰 또는 ID 토큰입니다.
            - GOOGLE/APPLE: id_token
            - KAKAO: access_token
            """,
        example = "eyJhbG...",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String token,

    @Schema(
        description = """
            APPLE 또는 GOOGLE 로그인 시 필요한 인가 코드(authorization code)입니다.
            - 탈퇴 시 연결 해제(revoke)에 사용될 리프레시 토큰 교환을 위해 필요합니다.
            - GOOGLE (네이티브): serverAuthCode
            - GOOGLE (웹 리다이렉트): 인증 코드로 반환된 code 값
            - APPLE: authorizationCode 원문
            - KAKAO: 사용하지 않음 (생략 가능)
            """,
        example = "c1234...",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String authorizationCode,

    @Schema(
        description = """
            GOOGLE 웹 리다이렉트 로그인 시에만 필요한 redirect_uri입니다.
            - Google authorization code 교환 시 사용했던 값과 정확히 일치해야 합니다.
            - KAKAO, APPLE, GOOGLE 네이티브 로그인의 경우 생략합니다.
            """,
        example = "https://tripfit.online/auth/google/callback",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String redirectUri
) {
}
