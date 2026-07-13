package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "개인 일정 목록 응답")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record PersonalScheduleResponse(
    @Schema(description = "개인 일정 목록")
    List<PersonalScheduleItemResponse> items
) {

  @Schema(description = "날짜별 개인 일정")
  public record PersonalScheduleItemResponse(
      @Schema(description = "ID", example = "550e8400-e29b-41d4-a716-446655440000")
      UUID id,

      @Schema(description = "날짜", example = "2026-08-03")
      LocalDate scheduleDate,

      @Schema(description = "오전", example = "IMPOSSIBLE")
      ScheduleStatus morningStatus,

      @Schema(description = "오후", example = "POSSIBLE")
      ScheduleStatus afternoonStatus,

      @Schema(description = "저녁", example = "POSSIBLE")
      ScheduleStatus eveningStatus,

      @Schema(description = "해당 날짜 전체 불확실", example = "false")
      boolean uncertain
  ) {
  }
}
// @formatter:on
