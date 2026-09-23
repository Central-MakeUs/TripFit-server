package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "기간 내 정기 일정과 개별 일정을 합친 달력 응답입니다. (GET /users/schedule/calendar)")
public record ScheduleCalendarResponse(
    @Schema(description = "조회 윈도우 내의 시작 날짜입니다.", example = "2026-08-01") LocalDate startDate,

    @Schema(description = "조회 윈도우 내의 종료 날짜입니다.", example = "2026-08-07") LocalDate endDate,

    @Schema(description = "일정 데이터가 존재하는 날짜만 포함된 달력 목록입니다. (sparse)") List<CalendarDayResponse> days
) {

  @Schema(description = "특정 날짜 1일의 정기+개별 병합 슬롯 결과입니다.")
  public record CalendarDayResponse(
      @Schema(description = "해당 날짜입니다.", example = "2026-08-03") LocalDate date,

      @Schema(description = "오전 슬롯 (00:00 ~ 13:00)의 병합된 상태입니다.",
          example = "IMPOSSIBLE") ScheduleStatus morningStatus,

      @Schema(description = "오후 슬롯 (13:00 ~ 18:00)의 병합된 상태입니다.",
          example = "IMPOSSIBLE") ScheduleStatus afternoonStatus,

      @Schema(description = "저녁 슬롯 (18:00 ~ 24:00)의 병합된 상태입니다.",
          example = "POSSIBLE") ScheduleStatus eveningStatus,

      @Schema(
          description = """
              해당 날짜의 불확실(Uncertain) 여부입니다.
              - 개별 일정에 불확실함이 명시된 경우에만 true로 반환됩니다.
              """,
          example = "false") boolean uncertain
  ) {
  }
}
