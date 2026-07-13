package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행 일정 추천 모드")
public enum RecommendationMode {
  @Schema(description = "기본 — 참석↑·연차↓·미정↓ 균형")
  BASIC,

  @Schema(description = "모두 참석 — 가능 인원 하드 필터 후 불가 최소화")
  ALL_ATTEND,

  @Schema(description = "휴가 아끼기 — 연차 소모 최소화")
  SAVE_VACATION,

  @Schema(description = "확실하게 가기 — 미정(uncertain) 최소화")
  CERTAIN
}
