package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 참여자 멤버십 상태")
public enum TripMemberStatus {
  @Schema(description = "멤버 등록됨 · 이 방 일정 확인 미완료. 방 입장·방 안 API 불가 (create/join 직후)")
  JOINED,

  @Schema(description = "일정 확인 완료. 방 입장·방 안 API 사용 가능")
  RESPONDED
}
