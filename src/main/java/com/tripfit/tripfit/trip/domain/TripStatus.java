package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    여행방의 현재 진행 상태입니다. (목록 필터 및 상세 화면 표시용)
    """)
public enum TripStatus {
  @Schema(
      description = """
          조율 중 상태입니다.
          - 방 생성 직후부터 여행 날짜 확정(또는 종료) 전까지 유지됩니다.
          - 멤버 참여, 메타 정보 수정, 일정 확인, 추천 기능이 가능합니다.
          """)
  ONGOING,

  @Schema(
      description = """
          일정 확정 상태입니다.
          - 방장이 추천된 날짜를 최종 확정한 이후의 상태입니다.
          - 더 이상 신규 초대가 불가능하며, 일정 달력은 읽기 전용 스냅샷으로 전환됩니다.
          """)
  CONFIRMED,

  @Schema(
      description = """
          기간 만료(종료) 상태입니다.
          - 희망 여행 기간(endRange)이 현재 날짜보다 과거인 경우의 상태입니다.
          - 기존 멤버 조회만 가능하며, 일정 달력은 읽기 전용 스냅샷으로 전환됩니다.
          """)
  EXPIRED
}
