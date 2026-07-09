package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 내 참여자 역할")
public enum TripMemberRole {

  @Schema(description = "방장(총대)")
  OWNER,

  @Schema(description = "일반 멤버")
  MEMBER
}
