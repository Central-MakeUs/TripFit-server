package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        여행방 멤버십 상태 (프론트·신규 개발자 필수).
        JOINED = 방장 create 직후만. 멤버는 절대 JOINED가 되지 않음(join → 곧바로 RESPONDED).
        RESPONDED = 방 입장 가능 상태(방장 confirm 후 / 멤버 join 시).
        초대 공유·상세 API = RESPONDED 이후(공유는 방장만). create 응답에 inviteCode 없음.
        """)
public enum TripMemberStatus {
  @Schema(
      description = """
          방장 전용. POST /trips 직후 ~ schedule/confirm 전.
          방 입장·상세·멤버·달력·Pin·카카오/링크 공유 모두 불가.
          일반 멤버 플로우에는 이 값이 나오지 않음.
          """)
  JOINED,

  @Schema(
      description = """
          일정 확인·가입 완료. 방장=confirm 후, 멤버=POST /join 시 즉시.
          방 입장·방 안 API 가능(추가 입장 조건 필요). 초대 공유는 방장만 이 상태 이후.
          """)
  RESPONDED
}
