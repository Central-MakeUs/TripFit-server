package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천이 도움되지 않은 이유 (status=NOT_HELPFUL일 때만 사용)")
public enum RecommendationFeedbackReason {
  @Schema(description = "참석 인원이 너무 적어요")
  TOO_FEW_ATTENDEES,

  @Schema(description = "연차를 너무 많이 써야 해요")
  TOO_MANY_VACATION_DAYS,

  @Schema(description = "불확실한 일정이 많이 포함됐어요")
  TOO_MANY_UNCERTAIN_SCHEDULES,

  @Schema(description = "추천 기준이 제 상황과 안 맞아요")
  CRITERIA_MISMATCH,

  @Schema(description = "기타 — reasonDetail(서술형) 필수")
  OTHER
}
