package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

// UI: 조율 중→ONGOING, 일정 확정→CONFIRMED, 종료→TERMINATED (정책서 2-4 · trip-room-api D5)
// 홈 필터 GET /trips?status= 도 이 enum 재사용 (ALL | ONGOING | CONFIRMED). 별도: TripMemberStatus,
// ScheduleStatus
@Schema(description = "여행방 진행 상태. 홈 전체 보기 status 필터도 동일 값(ALL 제외) 사용")
public enum TripStatus {
  @Schema(description = "조율 중 — 일정 수집·추천 전 (UI: 조율 중)")
  ONGOING,

  @Schema(description = "일정 확정 완료 (UI: 일정 확정). 신규 join 불가")
  CONFIRMED,

  @Schema(description = "취소됨 (방장 취소)")
  CANCELED,

  @Schema(description = "종료됨 — 희망 여행 시기(end_range) 경과 (UI: 종료)")
  TERMINATED
}
