package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "추천 생성 요청. POST /trips/{tripId}/recommendations (방장·ONGOING만)")
public record GenerateRecommendationsRequest(
    @Schema(description = "추천 모드", example = "BASIC",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull RecommendationMode mode
) {
}
