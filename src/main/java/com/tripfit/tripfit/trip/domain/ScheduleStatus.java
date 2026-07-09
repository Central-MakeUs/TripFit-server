package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "해당 날짜·시간대 가용성")
public enum ScheduleStatus {

  @Schema(description = "참여 가능")
  POSSIBLE,

  @Schema(description = "참여 불가")
  IMPOSSIBLE,

  @Schema(description = "미정")
  TBD
}
