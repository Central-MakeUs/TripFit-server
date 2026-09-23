package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = """
    개별 일정 업데이트 후, 최종 확정된 일정 응답입니다. (PATCH /users/schedule/personal)
    - 정기 일정, 개별 일정, 구글 캘린더 일정이 모두 병합된 최종 결과값입니다.
    """)
public record PersonalScheduleResponse(
    @Schema(
        description = """
            업데이트가 반영된 날짜별 최종 일정 목록입니다.
            - 일정이 전혀 없는 날짜는 응답에서 제외(sparse)됩니다.
            """) List<PersonalScheduleItemResponse> items
) {

  @Schema(description = "특정 날짜 1일의 정기+개별+구글 병합 최종 결과입니다.")
  public record PersonalScheduleItemResponse(
      @Schema(
          description = """
              개별 일정(personal_schedule)의 고유 ID입니다.
              - Upsert가 발생한 날짜는 항상 값이 존재합니다.
              """,
          example = "550e8400-e29b-41d4-a716-446655440000") UUID id,

      @Schema(description = "해당 일정의 날짜입니다.", example = "2026-08-03") LocalDate scheduleDate,

      @Schema(description = """
          오전 슬롯 (00:00 ~ 13:00)의 최종 확정 상태입니다.
          - 적용 우선순위: 개별 오버라이드 > (정기 일정 + 구글 캘린더)
          """,
          example = "IMPOSSIBLE") ScheduleStatus morningStatus,

      @Schema(description = """
          오후 슬롯 (13:00 ~ 18:00)의 최종 확정 상태입니다.
          - 적용 우선순위: 개별 오버라이드 > (정기 일정 + 구글 캘린더)
          """,
          example = "POSSIBLE") ScheduleStatus afternoonStatus,

      @Schema(description = """
          저녁 슬롯 (18:00 ~ 24:00)의 최종 확정 상태입니다.
          - 적용 우선순위: 개별 오버라이드 > (정기 일정 + 구글 캘린더)
          """,
          example = "POSSIBLE") ScheduleStatus eveningStatus,

      @Schema(description = """
          해당 날짜가 전체적으로 불확실(Uncertain)한지 여부입니다.
          - 등록된 개별 일정이 없으면 기본값은 false입니다.
          """,
          example = "false") boolean uncertain
  ) {
  }
}
