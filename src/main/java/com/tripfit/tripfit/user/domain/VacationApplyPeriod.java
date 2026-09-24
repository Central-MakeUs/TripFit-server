package com.tripfit.tripfit.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "연차 신청 가능 시점입니다.")
public enum VacationApplyPeriod {
  @Schema(description = "사전 신청 기간과 상관없음을 나타냅니다.")
  ANY,
  @Schema(description = "최소 1주일 전에 신청해야 함을 나타냅니다.")
  ONE_WEEK_BEFORE,
  @Schema(description = "최소 2주일 전에 신청해야 함을 나타냅니다.")
  TWO_WEEKS_BEFORE,
  @Schema(description = "최소 한 달 전에 신청해야 함을 나타냅니다.")
  ONE_MONTH_BEFORE
}
