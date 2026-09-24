package com.tripfit.tripfit.trip.recommendation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 후보 일정에 대한 참여자의 참석 분류를 나타냅니다. (전체참석, 부분참석, 불참)")
public enum AttendanceType {
  @Schema(description = "추천된 후보 기간의 모든 슬롯(오전, 오후, 저녁)에 참석 가능함을 나타냅니다.")
  FULL_ATTEND,

  @Schema(
      description = "후보 기간 전체 슬롯 수의 50% 이상을 하나의 연속된 구간으로 참석할 수 있음을 나타냅니다. (늦게 참석하거나 조기 귀가하는 경우만 인정됩니다)")
  PARTIAL_ATTEND,

  @Schema(description = "전체참석 및 부분참석 조건을 모두 만족하지 못하여 불참으로 간주됩니다.")
  NON_ATTEND
}
