package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "개인 일정 bulk upsert + 삭제 요청. PATCH /users/schedule/personal")
public record UpdatePersonalScheduleRequest(
    @Schema(
        description = "날짜별 upsert 항목. 슬롯 3개(morning/afternoon/evening)가 전부 null이고 uncertain=false인 항목은"
            + " 해당 날짜 row를 삭제(CLEAR)한다 — 오버라이드가 하나도 없는 상태와 동일하기 때문",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Valid List<PersonalScheduleItem> items
) {

  @Schema(description = "특정 날짜의 슬롯 오버라이드 + 날짜 단위 불확실")
  public record PersonalScheduleItem(
      @Schema(
          description = "날짜",
          example = "2026-08-03",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate scheduleDate,

      @Schema(
          description = "오전 [00:00, 13:00) 슬롯 오버라이드. null이면 이 슬롯은 손대지 않고 정기+구글 계산값을 그대로 씀",
          example = "IMPOSSIBLE",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) ScheduleStatus morningStatus,

      @Schema(
          description = "오후 [13:00, 18:00) 슬롯 오버라이드. null이면 이 슬롯은 손대지 않고 정기+구글 계산값을 그대로 씀",
          example = "POSSIBLE",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) ScheduleStatus afternoonStatus,

      @Schema(
          description = "저녁 [18:00, 24:00) 슬롯 오버라이드. null이면 이 슬롯은 손대지 않고 정기+구글 계산값을 그대로 씀",
          example = "POSSIBLE",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) ScheduleStatus eveningStatus,

      @Schema(description = "해당 날짜 전체 불확실 여부", example = "false") boolean uncertain
  ) {
  }
}
