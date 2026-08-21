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
        description = """
            APPLE 또는 GOOGLE 로그인 시 authorization code — 두 provider는 필수, KAKAO는 보내지 않음(값이 있어도 무시). 탈퇴 시 해당 provider 쪽 연결을 해제(revoke)하는 데 쓰일 refresh token을 교환하기 위한 값이다.

            GOOGLE(네이티브 앱 로그인): Google Sign-In SDK를 offline access로 설정했을 때 함께 내려오는 serverAuthCode 값.

            GOOGLE(브라우저 리다이렉트 로그인): authorization code(hybrid) 방식으로 요청했을 때 id_token과 함께 받는 code 값.

            APPLE: 네이티브 Sign in 인증 결과에 포함된 authorizationCode 원문.
            """,
        example = "c1234...",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String authorizationCode
) {
}
