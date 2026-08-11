package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "개인 일정 슬롯 단위 오버라이드 bulk upsert 요청. PATCH /users/schedule/personal")
public record UpdatePersonalScheduleRequest(
    @Schema(
        description = "날짜별 upsert 항목. 같은 scheduleDate 중복 금지",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Valid List<PersonalScheduleItem> items
) {

  @Schema(description = "특정 날짜의 슬롯 오버라이드·불확실 갱신 — slots·uncertain 각각 독립적으로 선택(둘 다 없으면 거부)")
  public record PersonalScheduleItem(
      @Schema(
          description = "날짜",
          example = "2026-08-03",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate scheduleDate,

      @Schema(
          description = "슬롯 오버라이드. 이 날짜의 슬롯을 안 건드리려면 필드 자체를 생략(null) — 보내면 3개 전부 필수",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid SlotUpdate slots,

      @Schema(
          description = "해당 날짜 전체 불확실 여부. 안 건드리려면 필드 자체를 생략(null)",
          example = "false",
          nullable = true,
          requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean uncertain
  ) {
  }

  @Schema(description = "슬롯 3개(오전/오후/저녁) 오버라이드 — 하나라도 건드리면 3개 전부 명시")
  public record SlotUpdate(
      @Schema(
          description = "오전 [00:00, 13:00) 슬롯 오버라이드",
          example = "IMPOSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus morningStatus,

      @Schema(
          description = "오후 [13:00, 18:00) 슬롯 오버라이드",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus afternoonStatus,

      @Schema(
          description = "저녁 [18:00, 24:00) 슬롯 오버라이드",
          example = "POSSIBLE",
          requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScheduleStatus eveningStatus
  ) {
  }
}
