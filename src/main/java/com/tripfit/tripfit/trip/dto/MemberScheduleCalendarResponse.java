package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "여행방 멤버 전원의 정기+개별 일정을 합친 달력. GET /trips/{tripId}/members/schedule/calendar")
public record MemberScheduleCalendarResponse(
    @Schema(description = "조회 시작 날짜 (여행방 희망 기간 startRange)") LocalDate startDate,
    @Schema(description = "조회 종료 날짜 (여행방 희망 기간 endRange)") LocalDate endDate,
    @Schema(
        description = "읽기 전용 여부. CONFIRMED·EXPIRED이면 true — 일정 snapshot 고정, 수정 불가") boolean readOnly,
    @Schema(description = "멤버별 정기+개별 합친 달력") List<MemberCalendar> members
) {

  @Schema(description = "멤버 1명의 정기+개별 합친 달력")
  public record MemberCalendar(
      @Schema(description = "사용자 ID") UUID userId,
      @Schema(description = "표시 이름 (동명이인 시 접미사)") String displayName,
      @Schema(description = "방 내 역할 (방장 OWNER / 일반 MEMBER)") TripMemberRole role,
      @Schema(
          description = "멤버십 상태. SCHEDULE_PENDING=방장 create 직후만, ACTIVE=방장 activate 후·멤버 join 시") TripMemberStatus memberStatus,
      @Schema(description = "합친 값이 있는 날짜만 포함 (sparse)") List<CalendarDay> days
  ) {
  }

  @Schema(description = "날짜 1일의 정기+개별을 합친 슬롯")
  public record CalendarDay(
      @Schema(description = "날짜") LocalDate date,
      @Schema(description = "오전 [00:00, 13:00) 합친 값") ScheduleStatus morningStatus,
      @Schema(description = "오후 [13:00, 18:00) 합친 값") ScheduleStatus afternoonStatus,
      @Schema(description = "저녁 [18:00, 24:00) 합친 값") ScheduleStatus eveningStatus,
      @Schema(description = "날짜 단위 불확실 (개인 일정 uncertain=true일 때)") boolean uncertain
  ) {
  }
}
