package com.tripfit.tripfit.trip.recommendation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방장이 여행 일정 확정을 취소하는 사유입니다.")
public enum UnconfirmReason {
  @Schema(description = "새로운 일정이 생겼어요")
  NEW_SCHEDULE_ADDED,

  @Schema(description = "참석 가능한 인원이 변경되었어요")
  ATTENDEE_AVAILABILITY_CHANGED,

  @Schema(description = "추천된 일정이 마음에 들지 않아요")
  RECOMMENDATION_UNSATISFACTORY,

  @Schema(description = "다른 조건으로 다시 추천받고 싶어요")
  WANT_OTHER_RECOMMENDATION,

  @Schema(description = "여행 계획이 변경되었어요")
  TRIP_PLAN_CHANGED,

  @Schema(description = "기타 사유입니다. 이 항목을 선택한 경우 상세 내용(reasonDetail)을 필수로 입력해야 합니다.")
  OTHER
}
