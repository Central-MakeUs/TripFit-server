package com.tripfit.tripfit.user.schedule.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "연차 신청 가능 시점")
public enum VacationApplyPeriod {
  @Schema(description = "상관없음")
  ANY,

  @Schema(description = "1주 전")
  ONE_WEEK_BEFORE,

  @Schema(description = "2주 전")
  TWO_WEEKS_BEFORE,

  @Schema(description = "한달 전")
  ONE_MONTH_BEFORE
}
