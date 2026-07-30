package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 근거에 방장이 남기는 도움 여부 피드백")
public enum RecommendationFeedbackStatus {
  @Schema(description = "이 추천이 도움이 되었음")
  HELPFUL,

  @Schema(description = "이 추천이 도움이 되지 않았음 (사유 필수)")
  NOT_HELPFUL
}
