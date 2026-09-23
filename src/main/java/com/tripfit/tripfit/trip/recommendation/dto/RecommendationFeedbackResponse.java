package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "해당 추천 후보에 대해 방장이 남긴 피드백 정보입니다. (피드백이 없으면 객체 자체가 null로 처리됩니다)")
public record RecommendationFeedbackResponse(
    @Schema(description = "추천 결과 도움 여부 상태입니다.") RecommendationFeedbackStatus status,

    @Schema(
        description = "도움이 되지 않은 이유입니다. (status=HELPFUL인 경우 null)",
        nullable = true) RecommendationFeedbackReason reason,

    @Schema(
        description = "직접 입력한 상세 사유입니다. (reason=OTHER일 때만 존재)",
        nullable = true) String reasonDetail
) {
}
