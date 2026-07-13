package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "여행방 진행 상태. GET /trips?status= 필터·TripDetailResponse.status 등에 사용")
public enum TripStatus {
  @Schema(description = "조율 중 — 일정 수집·추천 진행 (UI: 조율 중)")
  ONGOING,

  @Schema(description = "일정 확정 완료 (UI: 일정 확정). 신규 join 불가")
  CONFIRMED,

  @Schema(description = "취소됨 — 방장 취소 (UI: 취소)")
  CANCELED,

  @Schema(description = "종료됨 — 희망 여행 시기(end_range) 경과 (UI: 종료)")
  TERMINATED
}
