package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 멤버 개인 일정 요약")
public record MemberPersonalSummaryResponse(
    @Schema(description = "멤버별 개인 일정") List<MemberPersonal> members
) {

  @Schema(description = "멤버 1명")
  public record MemberPersonal(
      @Schema(description = "사용자 ID") UUID userId,
      @Schema(description = "표시 이름", example = "홍길동") String displayName,
      @Schema(description = "날짜별 일정") List<DayPersonal> days
  ) {
  }

  @Schema(description = "날짜 1일의 슬롯 + 불확실")
  public record DayPersonal(
      @Schema(description = "날짜", example = "2026-08-03") LocalDate scheduleDate,
      @Schema(description = "오전") ScheduleStatus morningStatus,
      @Schema(description = "오후") ScheduleStatus afternoonStatus,
      @Schema(description = "저녁") ScheduleStatus eveningStatus,
      @Schema(description = "날짜 전체 불확실") boolean uncertain
  ) {
  }
}
