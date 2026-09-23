package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 성공 응답입니다. (POST /auth/login)")
public record LoginResponse(
    @Schema(description = "TripFit API 호출용 액세스 토큰(JWT)입니다.",
        example = "eyJhbG...") String accessToken,

    @Schema(description = "액세스 토큰 만료까지 남은 시간(초)입니다.", example = "900") long expiresIn,

    @Schema(description = """
        로그인한 사용자 요약 정보입니다.
        - 사전 일정 입력 완료 여부(hasCompletedPreSchedule)가 포함됩니다.
        """) UserSummaryResponse user
) {
}
