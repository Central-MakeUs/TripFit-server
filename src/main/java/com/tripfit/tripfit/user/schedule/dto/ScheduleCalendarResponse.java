package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "기간 내 정기+개별 일정을 합친 달력. GET /users/schedule/calendar")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record ScheduleCalendarResponse(
    @Schema(description = "조회 시작 날짜 (조회 윈도우 내)", example = "2026-08-01")
    LocalDate startDate,

    @Schema(description = "조회 종료 날짜 (조회 윈도우 내)", example = "2026-08-07")
    LocalDate endDate,

    @Schema(description = "합친 값이 있는 날짜만 포함 (sparse)")
    List<CalendarDayResponse> days
) {

  @Schema(description = "날짜 1일의 정기+개별을 합친 슬롯")
  public record CalendarDayResponse(
      @Schema(description = "날짜", example = "2026-08-03")
      LocalDate date,

      @Schema(description = "오전 [00:00, 13:00) 합친 값", example = "IMPOSSIBLE")
      ScheduleStatus morningStatus,

      @Schema(description = "오후 [13:00, 18:00) 합친 값", example = "IMPOSSIBLE")
      ScheduleStatus afternoonStatus,

      @Schema(description = "저녁 [18:00, 24:00) 합친 값", example = "POSSIBLE")
      ScheduleStatus eveningStatus,

      @Schema(
          description = "날짜 단위 불확실. 개인 일정 uncertain=true일 때만 true 가능",
          example = "false")
      boolean uncertain
  ) {
  }
}
// @formatter:on
