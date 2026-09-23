package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "정기 일정 1건의 정보입니다. (GET/POST/PATCH /users/schedule/regular)")
public record RegularScheduleResponse(
    @Schema(description = "정기 일정 ID입니다.", example = "550e8400-e29b-41d4-a716-446655440000") UUID id,

    @Schema(description = "화면에 표시될 일정 이름입니다. (예: 출근, 수업 등)", example = "출근") String title,

    @Schema(
        description = """
            반복할 요일 목록입니다. (콤마로 구분)
            - 설정되지 않은 경우 null을 반환합니다.
            """,
        example = "MON,TUE,WED,THU,FRI",
        nullable = true) String daysOfWeek,

    @Schema(description = "일정 시작 시각입니다. 미설정 시 null입니다.", example = "09:00:00",
        nullable = true) LocalTime startTime,

    @Schema(description = "일정 종료 시각입니다. 미설정 시 null입니다.", example = "18:00:00",
        nullable = true) LocalTime endTime,

    @Schema(
        description = """
            오전 슬롯 (00:00 ~ 13:00)의 가능 상태입니다.
            - 시작/종료 시각을 기반으로 자동 계산됩니다.
            """,
        example = "IMPOSSIBLE",
        nullable = true) ScheduleStatus morningStatus,

    @Schema(
        description = """
            오후 슬롯 (13:00 ~ 18:00)의 가능 상태입니다.
            - 시작/종료 시각을 기반으로 자동 계산됩니다.
            """,
        example = "IMPOSSIBLE",
        nullable = true) ScheduleStatus afternoonStatus,

    @Schema(
        description = """
            저녁 슬롯 (18:00 ~ 24:00)의 가능 상태입니다.
            - 시작/종료 시각을 기반으로 자동 계산됩니다.
            """,
        example = "POSSIBLE",
        nullable = true) ScheduleStatus eveningStatus
) {

  @Schema(description = "정기 일정 목록 응답입니다. (GET /users/schedule/regular)")
  public record RegularScheduleListResponse(
      @Schema(description = "정기 일정 항목 목록") List<RegularScheduleResponse> items
  ) {
  }
}
