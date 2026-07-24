package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        여행방 진행 상태 (ONGOING | CONFIRMED | EXPIRED).
        목록 필터·상세 status 등에 사용.
        """)
public enum TripStatus {
  @Schema(
      description = """
          의미: 일정을 모으고 추천·조율하는 중.

          언제: 방 생성 직후 ~ 날짜 확정/취소/종료 전.

          가능: 참여·메타 수정·일정 확인·추천(구현 시). 확정 후에는 신규 참여 불가.
          """)
  ONGOING,

  @Schema(
      description = """
          의미: 여행 날짜가 확정됨.

          언제: 방장이 추천 후보(또는 직접 날짜)로 확정한 뒤.

          불가: 신규 초대 참여. 멤버 일정 달력은 스냅샷(읽기 전용).
          """)
  CONFIRMED,

  @Schema(
      description = """
          의미: 희망 여행 기간(endRange)이 지나 종료됨.

          언제: endRange < today (조회 시 lazy 또는 일 배치).

          가능: 기존 멤버 조회. 일정 달력은 스냅샷(읽기 전용).
          """)
  EXPIRED
}
