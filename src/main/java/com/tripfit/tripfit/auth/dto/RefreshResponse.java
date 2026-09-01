package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 재발급 성공 응답. POST /auth/refresh")
public record RefreshResponse(
    @Schema(description = "새 TripFit access JWT", example = "eyJhbG...") String accessToken,

    @Schema(
        description = "새 refresh token. 기존 값은 이 응답과 동시에 폐기되므로 반드시 저장해 교체해야 한다(RTR)",
        example = "550e8400-e29b-41d4-a716-446655440000") String refreshToken,

    @Schema(description = "access JWT 만료까지 남은 시간(초)", example = "7200") long expiresIn
) {
}
