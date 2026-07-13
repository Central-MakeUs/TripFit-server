package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 성공 응답")
public record LoginResponse(
    @Schema(description = "TripFit access JWT", example = "eyJhbG...") String accessToken,

    @Schema(
        description = "opaque refresh token",
        example = "550e8400-e29b-41d4-a716-446655440000") String refreshToken,

    @Schema(description = "access JWT 만료까지 남은 초", example = "7200") long expiresIn,

    @Schema(description = "로그인한 사용자 요약") UserSummaryResponse user
) {
}
