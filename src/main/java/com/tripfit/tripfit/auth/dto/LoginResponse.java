package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 성공 응답. POST /auth/login")
public record LoginResponse(
    @Schema(description = "TripFit API 호출용 access JWT", example = "eyJhbG...") String accessToken,

    @Schema(description = "access JWT 만료까지 남은 시간(초)", example = "900") long expiresIn,

    @Schema(description = "로그인한 사용자 요약 (hasPreSchedule·isAllFree 포함)") UserSummaryResponse user
) {
}
