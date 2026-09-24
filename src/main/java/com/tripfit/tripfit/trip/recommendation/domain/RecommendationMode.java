package com.tripfit.tripfit.trip.recommendation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행 일정 추천 모드를 나타냅니다.")
public enum RecommendationMode {
  @Schema(description = "기본 추천 모드입니다. 불참률, 부분참석 비율, 불확실 인원 비율, 연차 일수를 모두 동일한 가중치로 반영합니다.")
  BASIC,

  @Schema(description = "모두 참석 모드입니다. 불참률과 부분참석 비율의 가중치를 높여 모든 인원이 참석할 수 있는 가능성을 최우선으로 반영합니다.")
  ALL_ATTEND,

  @Schema(description = "휴가 아끼기 모드입니다. 1인당 평균 연차 일수의 가중치를 높여 연차 소모를 최소화합니다.")
  SAVE_VACATION,

  @Schema(description = "확실하게 가기 모드입니다. 불확실 인원 비율의 가중치를 높여 일정이 불확실한 날짜를 최소화합니다.")
  CERTAIN
}
