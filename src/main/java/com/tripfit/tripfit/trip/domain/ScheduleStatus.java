package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "슬롯 가능/불가. 날짜 단위 불확실은 PersonalSchedule.uncertain 사용")
public enum ScheduleStatus {
  @Schema(description = "참여 가능")
  POSSIBLE,

  @Schema(description = "참여 불가")
  IMPOSSIBLE
}
