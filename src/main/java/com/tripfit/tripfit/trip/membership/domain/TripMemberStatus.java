package com.tripfit.tripfit.trip.membership.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        여행방 안에서의 멤버 진행 상태 (SCHEDULE_PENDING | ACTIVE).
        이름 그대로 "일정 확인 대기중"과 "방 활동 가능"을 뜻한다. 방장·참여자 모두 방에 들어오는 순간
        SCHEDULE_PENDING으로 시작해, 이 방 일정 확인을 마치고 activate를 호출해야 ACTIVE가 된다.
        """)
public enum TripMemberStatus {
  @Schema(
      description = """
          의미: 방에 자리는 잡았지만, 아직 이 방 일정 확인을 끝내지 않아 방 안 입장이 막힌 상태.

          언제: 방장=POST /trips 직후, 참여자=POST /trips/join 직후.
          둘 다 POST /trips/{tripId}/activate 전까지 이 값이다.

          불가: 방 상세·멤버 목록·달력·초대 링크/코드 공유.
          가능: 홈 목록 노출·Pin(방 안 콘텐츠가 아닌 개인 설정이라 허용).
          """)
  SCHEDULE_PENDING,

  @Schema(
      description = """
          의미: 이 방 일정 확인을 끝내 방 안을 쓸 수 있는 상태.

          언제: 방장·참여자 모두 POST /trips/{tripId}/activate 후.

          가능: 방 입장·방 안 API.
          초대 공유는 방장만, 그리고 이 상태 이후에만.
          """)
  ACTIVE
}
