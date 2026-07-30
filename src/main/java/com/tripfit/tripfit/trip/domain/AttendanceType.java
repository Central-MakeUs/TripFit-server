package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 후보 일정에 대한 참여자 참석 분류 (전체참석/부분참석/불참)")
public enum AttendanceType {
  @Schema(description = "후보 기간의 모든 슬롯(오전/오후/저녁)에 참석 가능")
  FULL_ATTEND,

  @Schema(
      description = "후보 기간 전체 슬롯 수의 50% 이상을 하나의 연속된 구간으로 참석 가능(늦참·조기귀가만 인정, 중간 이탈 후 재합류는 불인정)")
  PARTIAL_ATTEND,

  @Schema(description = "전체참석·부분참석 조건을 모두 만족하지 못함")
  NON_ATTEND
}
