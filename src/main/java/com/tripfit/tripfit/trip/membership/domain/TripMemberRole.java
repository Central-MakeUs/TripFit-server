package com.tripfit.tripfit.trip.membership.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "여행방 내에서의 참여자 역할입니다. (OWNER: 방장, MEMBER: 일반 멤버)")
public enum TripMemberRole {
  @Schema(
      description = """
          방장(총대)입니다.
          - 방 생성, 정보 수정, 일정 확정, 멤버 내보내기 권한을 가집니다.
          """)
  OWNER,

  @Schema(description = "일반 멤버입니다.")
  MEMBER
}
