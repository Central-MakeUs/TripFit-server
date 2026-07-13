package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(
    description = "정기 일정 1건 응답. GET /users/schedule/regular · POST /users/schedule/regular · PATCH /users/schedule/regular/{id}")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record RegularScheduleResponse(
    @Schema(description = "정기 일정 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "표시명 (출근·수업 등)", example = "출근")
    String title,

    @Schema(
        description = "반복 요일. Weekday 콤마 구분(MON~SUN). 미설정 시 null",
        example = "MON,TUE,WED,THU,FRI",
        nullable = true)
    String daysOfWeek,

    @Schema(description = "시작 시각. 미설정 시 null", example = "09:00:00", nullable = true)
    LocalTime startTime,

    @Schema(description = "종료 시각. 미설정 시 null", example = "18:00:00", nullable = true)
    LocalTime endTime,

    @Schema(
        description = "오전 [00:00, 13:00) 슬롯. start/end 시각에서 파생",
        example = "IMPOSSIBLE",
        nullable = true)
    ScheduleStatus morningStatus,

    @Schema(
        description = "오후 [13:00, 18:00) 슬롯. start/end 시각에서 파생",
        example = "IMPOSSIBLE",
        nullable = true)
    ScheduleStatus afternoonStatus,

    @Schema(
        description = "저녁 [18:00, 24:00) 슬롯. start/end 시각에서 파생",
        example = "POSSIBLE",
        nullable = true)
    ScheduleStatus eveningStatus,

    @Schema(description = "여행당 최대 연차 일수 (기본 2, 허용 0~10)", example = "2")
    int maxVacationDays,

    @Schema(description = "연차 신청 가능 시점. 미설정 시 null", example = "ONE_WEEK_BEFORE", nullable = true)
    VacationApplyPeriod vacationApplyPeriod,

    @Schema(description = "반차 사용 가능 여부 (기본 false)", example = "false")
    boolean halfVacationAvailable,

    @Schema(description = "공휴일 휴무 여부 (기본 true)", example = "true")
    boolean holidayRest
) {

  @Schema(description = "정기 일정 목록 응답. GET /users/schedule/regular")
  public record RegularScheduleListResponse(
      @Schema(description = "정기 일정 항목 목록") List<RegularScheduleResponse> items
  ) {
  }
}
// @formatter:on
