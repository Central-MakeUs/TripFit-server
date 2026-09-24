package com.tripfit.tripfit.trip.schedule.dto;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(
    description = "여행방 멤버 전원의 정기 일정과 개별 일정을 합산한 달력 응답 정보입니다.")
public record MemberScheduleCalendarResponse(
    @Schema(description = "조회 시작 날짜입니다. (여행방의 희망 기간 시작일)") LocalDate startDate,
    @Schema(description = "조회 종료 날짜입니다. (여행방의 희망 기간 종료일)") LocalDate endDate,
    @Schema(
        description = """
            달력의 읽기 전용 여부입니다.
            - 여행방이 CONFIRMED 또는 EXPIRED 상태일 경우 true이며, 일정은 수정할 수 없는 스냅샷으로 고정됩니다.
            """) boolean readOnly,
    @Schema(description = "멤버별로 정기 일정과 개별 일정을 합친 달력 정보입니다.") List<MemberCalendar> members
) {

  @Schema(description = "단일 멤버의 정기 일정과 개별 일정을 합친 달력 정보입니다.")
  public record MemberCalendar(
      @Schema(description = "사용자의 고유 식별자(ID)입니다.") UUID userId,
      @Schema(description = "사용자의 표시 이름입니다. 동명이인이 있는 경우 접미사가 포함됩니다.") String displayName,
      @Schema(description = "여행방 내에서의 역할입니다. (OWNER: 방장, MEMBER: 일반 멤버)") TripMemberRole role,
      @Schema(
          description = "멤버십 상태를 나타냅니다. (SCHEDULE_PENDING: 방장이 방을 생성한 직후, ACTIVE: 방장이 일정을 활성화했거나 멤버가 참여 완료함)") TripMemberStatus memberStatus,
      @Schema(description = "일정 데이터가 존재하는 날짜만 포함된 배열입니다.") List<CalendarDay> days
  ) {
  }

  @Schema(description = "하루 단위의 정기 및 개별 일정을 합친 슬롯 상태입니다.")
  public record CalendarDay(
      @Schema(description = "일정이 해당하는 날짜입니다.") LocalDate date,
      @Schema(description = "오전(00:00 ~ 13:00) 슬롯의 일정 상태입니다.") ScheduleStatus morningStatus,
      @Schema(description = "오후(13:00 ~ 18:00) 슬롯의 일정 상태입니다.") ScheduleStatus afternoonStatus,
      @Schema(description = "저녁(18:00 ~ 24:00) 슬롯의 일정 상태입니다.") ScheduleStatus eveningStatus,
      @Schema(description = "해당 날짜 전체의 일정이 불확실(uncertain)한지 여부를 나타냅니다.") boolean uncertain
  ) {
  }
}
