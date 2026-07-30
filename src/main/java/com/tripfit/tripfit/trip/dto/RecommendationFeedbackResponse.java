package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.domain.RecommendationFeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방장이 이 후보에 남긴 도움 여부 피드백. 남긴 적 없으면 이 객체 자체가 null")
public record RecommendationFeedbackResponse(
    @Schema(description = "도움 여부") RecommendationFeedbackStatus status,

    @Schema(description = "도움 안 된 이유. status=HELPFUL이면 null",
        nullable = true) RecommendationFeedbackReason reason,

    @Schema(description = "reason=OTHER일 때 직접 입력 텍스트", nullable = true) String reasonDetail
) {
}
