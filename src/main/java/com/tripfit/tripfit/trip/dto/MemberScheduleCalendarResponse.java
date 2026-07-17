package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 멤버 전원 effective 일정 달력")
public record MemberScheduleCalendarResponse(
    @Schema(description = "조회 시작 날짜") LocalDate startDate,
    @Schema(description = "조회 종료 날짜") LocalDate endDate,
    @Schema(description = "멤버별 effective 달력") List<MemberCalendar> members
) {

  @Schema(description = "멤버 1명의 effective 달력")
  public record MemberCalendar(
      @Schema(description = "사용자 ID") UUID userId,
      @Schema(description = "표시 이름") String displayName,
      @Schema(description = "방 내 역할") TripMemberRole role,
      @Schema(description = "일정 응답 상태") TripMemberStatus memberStatus,
      @Schema(description = "effective가 있는 날짜만 (sparse)") List<CalendarDay> days
  ) {
  }

  @Schema(description = "날짜 1일의 effective 슬롯")
  public record CalendarDay(
      @Schema(description = "날짜") LocalDate date,
      @Schema(description = "오전 effective") ScheduleStatus morningStatus,
      @Schema(description = "오후 effective") ScheduleStatus afternoonStatus,
      @Schema(description = "저녁 effective") ScheduleStatus eveningStatus,
      @Schema(description = "날짜 단위 불확실") boolean uncertain
  ) {
  }
}
