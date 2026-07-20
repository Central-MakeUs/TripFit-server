package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 여행방 목록 뷰 (D5)")
public enum TripListScope {
  @Schema(description = "진행 중인 여행 캐러셀 — end_range >= today · Pin 정렬")
  ONGOING,

  @Schema(description = "전체 여행 보기 — Pin 미적용 · last_activity_at만")
  ALL
}
