package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackReason;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    추천 결과 피드백 생성 및 수정 요청입니다. (PATCH /trips/{tripId}/recommendations/{rank}/feedback)
    - 방장 전용 기능입니다.
    - status=NOT_HELPFUL인 경우 reason 값이 필수입니다.
    - reason=OTHER인 경우 reasonDetail 값이 필수입니다.
    """)
public record SaveRecommendationFeedbackRequest(
    @Schema(
        description = "추천 결과 도움 여부입니다.",
        example = "HELPFUL",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull RecommendationFeedbackStatus status,

    @Schema(
        description = "도움이 되지 않은 이유입니다. (status=NOT_HELPFUL 일 때 필수)",
        nullable = true) RecommendationFeedbackReason reason,

    @Schema(
        description = "직접 입력한 상세 이유입니다. (reason=OTHER 일 때 필수 / 그 외에는 무시됨)",
        nullable = true) String reasonDetail
) {
}
