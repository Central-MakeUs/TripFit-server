package com.tripfit.tripfit.trip.membership.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    여행방 내에서의 개별 멤버 상태입니다.
    - 방장과 참여자 모두 최초 참여 시 `SCHEDULE_PENDING`으로 시작합니다.
    - 개인 일정 확인(activate)을 완료해야 `ACTIVE` 상태가 됩니다.
    """)
public enum TripMemberStatus {
  @Schema(
      description = """
          일정 확인 대기 상태입니다.
          - 방에 참여했지만 일정 확인 플로우를 완료하지 않은 상태입니다.
          - 방 상세, 멤버 목록, 달력 조회, 초대 링크/코드 공유가 불가능합니다.
          - 홈 목록 노출 및 상단 고정(Pin)은 가능합니다.
          """)
  SCHEDULE_PENDING,

  @Schema(
      description = """
          방 활동 가능 상태입니다.
          - 일정 확인 플로우(activate)를 완료하여 방 안의 모든 기능을 사용할 수 있는 상태입니다.
          - 초대 공유 기능은 이 상태 이후 방장에게만 열립니다.
          """)
  ACTIVE
}
