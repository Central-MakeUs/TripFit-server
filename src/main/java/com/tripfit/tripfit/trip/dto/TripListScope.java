package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 여행방 목록 조회 뷰 구분. GET /trips?scope=")
public enum TripListScope {
  @Schema(description = "진행 중 여행 캐러셀 — end_range >= 오늘, Pin 우선 정렬")
  ONGOING,

  @Schema(description = "전체 여행 보기 — Pin 미적용, last_activity_at 내림차순")
  ALL
}
