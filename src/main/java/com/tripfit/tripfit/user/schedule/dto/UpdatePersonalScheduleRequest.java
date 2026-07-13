package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "개인 일정 bulk upsert 요청")
public record UpdatePersonalScheduleRequest(
    @Schema(description = "날짜별 항목",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Valid List<PersonalScheduleItem> items
) {

  @Schema(description = "특정 날짜의 슬롯 가능/불가 + 날짜 단위 불확실")
  public record PersonalScheduleItem(
      @Schema(
          description = "날짜",
          example = "2026-08-03",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate scheduleDate,

      @Schema(
          description = "오전",
          example = "IMPOSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus morningStatus,

      @Schema(
          description = "오후",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus afternoonStatus,

      @Schema(
          description = "저녁",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus eveningStatus,

      @Schema(description = "해당 날짜 전체 불확실", example = "false") boolean uncertain
  ) {
  }
}
