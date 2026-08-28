package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "반영된 날짜들의 정기+개별+구글 합친 최종 확정값. PATCH /users/schedule/personal")

public record PersonalScheduleResponse(
    @Schema(
        description = "반영된 날짜별 최종 확정값 목록. 아무 신호도 없는 날짜는 생략(sparse)") List<PersonalScheduleItemResponse> items
) {

  @Schema(description = "날짜 1건의 정기+개별+구글 합친 최종 확정값")
  public record PersonalScheduleItemResponse(
      @Schema(
          description = "개별 일정(personal_schedule) row ID. upsert된 날짜는 항상 row가 존재하므로 non-null",
          example = "550e8400-e29b-41d4-a716-446655440000") UUID id,

      @Schema(description = "날짜", example = "2026-08-03") LocalDate scheduleDate,

      @Schema(description = "오전 [00:00, 13:00) 최종 확정값. 개별 오버라이드 > 정기⊕구글",
          example = "IMPOSSIBLE") ScheduleStatus morningStatus,

      @Schema(description = "오후 [13:00, 18:00) 최종 확정값. 개별 오버라이드 > 정기⊕구글",
          example = "POSSIBLE") ScheduleStatus afternoonStatus,

      @Schema(description = "저녁 [18:00, 24:00) 최종 확정값. 개별 오버라이드 > 정기⊕구글",
          example = "POSSIBLE") ScheduleStatus eveningStatus,

      @Schema(description = "해당 날짜 전체 불확실 여부. 개별 일정 row가 없으면 false",
          example = "false") boolean uncertain
  ) {
  }
}
