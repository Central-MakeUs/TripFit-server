package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 재발급 성공 응답입니다. (POST /auth/refresh)")
public record RefreshResponse(
    @Schema(description = "새로 발급된 TripFit 액세스 토큰(JWT)입니다.",
        example = "eyJhbG...") String accessToken,

    @Schema(description = "액세스 토큰 만료까지 남은 시간(초)입니다.", example = "900") long expiresIn
) {
}
