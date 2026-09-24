package com.tripfit.tripfit.trip.recommendation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방장이 추천 결과 근거 화면에서 남기는 도움 여부 피드백입니다.")
public enum RecommendationFeedbackStatus {
  @Schema(description = "해당 추천 결과가 도움이 되었음을 나타냅니다.")
  HELPFUL,

  @Schema(description = "이 추천 결과가 도움이 되지 않았음을 나타냅니다. (사유 입력 필수)")
  NOT_HELPFUL
}
