package com.tripfit.tripfit.trip.recommendation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행 일정 추천 모드")
public enum RecommendationMode {
  @Schema(description = "기본 — 불참률·부분참석비율·불확실인원비율·연차일수 4개 항목을 동일 가중치로 반영")
  BASIC,

  @Schema(description = "모두 참석 — 하드 필터 아님. 불참률·부분참석비율 가중치를 크게 둬 전원 참석 가능성을 우선 반영")
  ALL_ATTEND,

  @Schema(description = "휴가 아끼기 — 1인당 평균 연차 일수 가중치를 크게 둬 연차 소모 최소화")
  SAVE_VACATION,

  @Schema(description = "확실하게 가기 — 불확실 인원 비율 가중치를 크게 둬 불확실 일정 최소화")
  CERTAIN
}
