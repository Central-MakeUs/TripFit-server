package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    여행 일정 추천 생성 요청입니다. (POST /trips/{tripId}/recommendations)
    - 방장만 호출 가능하며, 여행방이 ONGOING 상태일 때만 유효합니다.
    """)
public record GenerateRecommendationsRequest(
    @Schema(
        description = "추천 모드입니다.",
        example = "BASIC",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull RecommendationMode mode
) {
}
