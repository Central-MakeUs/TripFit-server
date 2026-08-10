package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 탭 시 이동할 화면")
public enum LandingType {
  @Schema(description = "여행방 상세 화면")
  TRAVEL_ROOM_DETAIL,

  @Schema(description = "일정 관리 화면")
  SCHEDULE_MANAGEMENT
}
