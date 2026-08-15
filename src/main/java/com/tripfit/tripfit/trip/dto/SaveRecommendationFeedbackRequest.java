package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.domain.RecommendationFeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    description = "추천 근거 피드백 upsert 요청. PUT /trips/{tripId}/recommendations/{rank}/feedback (방장 전용)."
        + " status=NOT_HELPFUL이면 reason 필수, reason=OTHER면 reasonDetail 필수")
// @formatter:off
public record SaveRecommendationFeedbackRequest(
    @Schema(description = "도움 여부", example = "HELPFUL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    RecommendationFeedbackStatus status,

    @Schema(description = "도움 안 된 이유. status=NOT_HELPFUL일 때 필수", nullable = true)
    RecommendationFeedbackReason reason,

    @Schema(description = "reason=OTHER일 때 직접 입력 텍스트. 그 외엔 무시됨", nullable = true)
    String reasonDetail
) {
}
// @formatter:on
