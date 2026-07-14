package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "기간 내 날짜별 effective(합친) 일정 달력 응답")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record ScheduleCalendarResponse(
    @Schema(description = "조회 시작 날짜", example = "2026-08-01")
    LocalDate startDate,

    @Schema(description = "조회 종료 날짜", example = "2026-08-07")
    LocalDate endDate,

    @Schema(description = "effective가 있는 날짜만 (sparse)")
    List<CalendarDayResponse> days
) {

  @Schema(description = "날짜 1일의 effective 슬롯")
  public record CalendarDayResponse(
      @Schema(description = "날짜", example = "2026-08-03")
      LocalDate date,

      @Schema(description = "오전 effective", example = "IMPOSSIBLE")
      ScheduleStatus morningStatus,

      @Schema(description = "오후 effective", example = "IMPOSSIBLE")
      ScheduleStatus afternoonStatus,

      @Schema(description = "저녁 effective", example = "POSSIBLE")
      ScheduleStatus eveningStatus,

      @Schema(description = "날짜 단위 불확실 (personal 있을 때만 true 가능)", example = "false")
      boolean uncertain
  ) {
  }
}
// @formatter:on
