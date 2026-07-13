package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "개인 일정 bulk upsert + 삭제 요청. PATCH /users/schedule/personal")
public record UpdatePersonalScheduleRequest(
    @Schema(
        description = "날짜별 upsert 항목. 비어 있거나 생략 가능(deletedDates만으로 CLEAR)",
        nullable = true) @Valid List<PersonalScheduleItem> items,

    @Schema(
        description = "삭제할 날짜 목록. 해당 (user, date) row 삭제(해당 날 CLEAR). null/빈 배열 가능",
        nullable = true,
        example = "[\"2026-08-04\"]") List<LocalDate> deletedDates
) {

  @Schema(description = "특정 날짜의 슬롯 가능/불가 + 날짜 단위 불확실")
  public record PersonalScheduleItem(
      @Schema(
          description = "날짜",
          example = "2026-08-03",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate scheduleDate,

      @Schema(
          description = "오전 [00:00, 13:00) 슬롯",
          example = "IMPOSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus morningStatus,

      @Schema(
          description = "오후 [13:00, 18:00) 슬롯",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus afternoonStatus,

      @Schema(
          description = "저녁 [18:00, 24:00) 슬롯",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus eveningStatus,

      @Schema(description = "해당 날짜 전체 불확실 여부", example = "false") boolean uncertain
  ) {
  }
}
