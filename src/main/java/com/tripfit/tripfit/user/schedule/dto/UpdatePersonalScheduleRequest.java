package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "개별 일정의 슬롯 단위 오버라이드(Bulk Upsert) 요청입니다. (PATCH /users/schedule/personal)")
public record UpdatePersonalScheduleRequest(
    @Schema(
        description = """
            날짜별 Upsert 항목 목록입니다.
            - 동일한 날짜(scheduleDate)가 중복 포함되어서는 안 됩니다.
            """,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Valid List<PersonalScheduleItem> items
) {

  @Schema(description = """
      특정 날짜의 슬롯 오버라이드 및 불확실(Uncertain) 상태 갱신 항목입니다.
      - 슬롯(slots)과 불확실 상태(uncertain)는 각각 독립적으로 적용됩니다.
      - 두 값 모두 생략(null)된 채 요청하면 거부됩니다.
      """)
  public record PersonalScheduleItem(
      @Schema(
          description = "오버라이드를 적용할 날짜입니다.",
          example = "2026-08-03",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate scheduleDate,

      @Schema(
          description = """
              수정할 슬롯 오버라이드 정보입니다.
              - 값을 포함하여 보낼 경우 오전, 오후, 저녁 3개 슬롯을 모두 명시해야 합니다.
              - 해당 날짜의 슬롯 상태를 변경하지 않으려면 필드 자체를 생략(null)합니다.
              """,
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid SlotUpdate slots,

      @Schema(
          description = """
              해당 날짜 전체의 불확실(Uncertain) 여부입니다.
              - 상태를 변경하지 않으려면 필드 자체를 생략(null)합니다.
              """,
          example = "false",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean uncertain
  ) {
  }

  @Schema(description = "오전/오후/저녁 3개 슬롯의 오버라이드 상태입니다. (수정 시 3개 필드 모두 명시)")
  public record SlotUpdate(
      @Schema(
          description = "오전 슬롯 (00:00 ~ 13:00) 오버라이드 상태입니다.",
          example = "IMPOSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus morningStatus,

      @Schema(
          description = "오후 슬롯 (13:00 ~ 18:00) 오버라이드 상태입니다.",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus afternoonStatus,

      @Schema(
          description = "저녁 슬롯 (18:00 ~ 24:00) 오버라이드 상태입니다.",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus eveningStatus
  ) {
  }
}
