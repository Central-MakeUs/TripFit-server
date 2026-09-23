package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        알림을 탭(클릭)했을 때 이동할 대상 화면 종류입니다.
        """)
public enum LandingType {
  @Schema(
      description = """
          여행방 상세 화면으로 이동합니다.
          """)
  TRAVEL_ROOM_DETAIL,
  @Schema(
      description = """
          내 일정 관리 화면으로 이동합니다.
          """)
  SCHEDULE_MANAGEMENT
}
