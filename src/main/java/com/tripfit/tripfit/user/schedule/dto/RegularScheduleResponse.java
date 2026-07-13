package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "정기 일정 응답 (SlotStatuses 포함)")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record RegularScheduleResponse(
    @Schema(description = "정기 일정 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "표시명", example = "출근")
    String title,

    @Schema(description = "반복 요일", example = "MON,TUE,WED,THU,FRI", nullable = true)
    String daysOfWeek,

    @Schema(description = "시작 시각", example = "09:00:00", nullable = true)
    LocalTime startTime,

    @Schema(description = "종료 시각", example = "18:00:00", nullable = true)
    LocalTime endTime,

    @Schema(description = "오전 슬롯 상태", example = "IMPOSSIBLE", nullable = true)
    ScheduleStatus morningStatus,

    @Schema(description = "오후 슬롯 상태", example = "IMPOSSIBLE", nullable = true)
    ScheduleStatus afternoonStatus,

    @Schema(description = "저녁 슬롯 상태", example = "POSSIBLE", nullable = true)
    ScheduleStatus eveningStatus,

    @Schema(description = "여행당 최대 연차 일수 (default 2, max 10)", example = "2")
    int maxVacationDays,

    @Schema(description = "연차 신청 가능 시점", example = "ONE_WEEK_BEFORE", nullable = true)
    VacationApplyPeriod vacationApplyPeriod,

    @Schema(description = "반차 사용 가능 여부 (default false)", example = "false")
    boolean halfVacationAvailable,

    @Schema(description = "공휴일 휴무 여부 (default true)", example = "true")
    boolean holidayRest
) {

  @Schema(description = "정기 일정 목록 응답")
  public record RegularScheduleListResponse(
      @Schema(description = "정기 일정 목록")
      List<RegularScheduleResponse> items
  ) {
  }
}
// @formatter:on
