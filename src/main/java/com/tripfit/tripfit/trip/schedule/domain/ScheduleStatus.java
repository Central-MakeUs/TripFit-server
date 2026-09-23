package com.tripfit.tripfit.trip.schedule.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    일정 슬롯의 참여 가능/불가 상태입니다.
    - 참고: '불확실' 여부는 슬롯 단위가 아니라 날짜 단위(uncertain)로 별도 관리됩니다.
    """)
public enum ScheduleStatus {
  @Schema(description = "참여 가능합니다. (UI 표시: 가능)")
  POSSIBLE,
  @Schema(description = "참여 불가합니다. (UI 표시: 불가)")
  IMPOSSIBLE
}
