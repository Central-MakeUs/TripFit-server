package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 참여자 멤버십 상태")
public enum TripMemberStatus {
  @Schema(description = "멤버 row 있음 · 이 방 일정 확인 미완료 (방장 create 직후). 방 입장 불가 (#39)")
  JOINED,

  @Schema(description = "일정 확인·가입 완료 — 방 입장 가능 (canEnterRoom도 필요)")
  RESPONDED
}
