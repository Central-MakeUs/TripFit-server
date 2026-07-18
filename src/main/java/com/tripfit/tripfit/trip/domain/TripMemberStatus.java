package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        여행방 안에서의 멤버 진행 상태 (JOINED | RESPONDED).
        """)
public enum TripMemberStatus {
  @Schema(
      description = """
          의미: 방장이 방을 만들었지만, 아직 이 방 일정 확인을 끝내지 않은 상태.

          언제: POST /trips 직후 ~ POST /trips/{tripId}/schedule/confirm 전.
          일반 멤버(초대 참여)는 이 값이 되지 않는다.

          불가: 방 상세·멤버 목록·달력·Pin·초대 링크/코드 공유.
          """)
  JOINED,

  @Schema(
      description = """
          의미: 이 방 일정 확인(또는 초대 참여)을 끝내 방 안을 쓸 수 있는 상태.

          언제: 방장=schedule/confirm 후, 일반 멤버=POST /trips/join 시 즉시.

          가능: 방 입장·방 안 API
          (추가로 전역 일정≥1건 또는 전부 free 필요).
          초대 공유는 방장만, 그리고 이 상태 이후에만.
          """)
  RESPONDED
}
