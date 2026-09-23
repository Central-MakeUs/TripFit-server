package com.tripfit.tripfit.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "홈 화면의 여행방 목록 뷰 타입입니다. (GET /trips?scope=)")
public enum TripListScope {
  @Schema(
      description = """
          진행 중인 여행방 목록 (캐러셀용)입니다.
          - end_range가 오늘 이후인 방만 조회됩니다.
          - 상단 고정(Pin)된 방이 우선 정렬됩니다.
          """)
  ONGOING,

  @Schema(
      description = """
          전체 여행방 목록입니다.
          - 상단 고정(Pin) 여부가 정렬에 반영되지 않습니다.
          - 최근 활동 시각(last_activity_at) 내림차순으로 정렬됩니다.
          """)
  ALL
}
