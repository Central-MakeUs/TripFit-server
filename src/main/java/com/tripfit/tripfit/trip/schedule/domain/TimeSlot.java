package com.tripfit.tripfit.trip.schedule.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = """
    일정 입력 시간대(슬롯) 단위입니다. (정기/개별 일정 공통 사용)
    - 시간대는 반개구간 [시작시간, 종료시간) 형태로 적용됩니다.
    """)
public enum TimeSlot {
  @Schema(description = "오전(00:00 ~ 13:00) 슬롯입니다.")
  MORNING(LocalTime.MIDNIGHT, LocalTime.of(13, 0)),

  @Schema(description = "오후(13:00 ~ 18:00) 슬롯입니다.")
  AFTERNOON(LocalTime.of(13, 0), LocalTime.of(18, 0)),

  @Schema(description = "저녁(18:00 ~ 24:00) 슬롯입니다.")
  EVENING(LocalTime.of(18, 0), null);

  private final LocalTime startInclusive;

  private final LocalTime endExclusive;

  TimeSlot(LocalTime startInclusive, LocalTime endExclusive) {
    this.startInclusive = startInclusive;
    this.endExclusive = endExclusive;
  }

  public LocalTime getStartInclusive() {
    return startInclusive;
  }

  public LocalTime getEndExclusive() {
    return endExclusive;
  }

  public boolean overlaps(LocalTime rangeStart, LocalTime rangeEnd) {
    if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
      return false;
    }
    boolean startsBeforeSlotEnd =
        endExclusive == null || rangeStart.isBefore(endExclusive);
    boolean endsAfterSlotStart = rangeEnd.isAfter(startInclusive);
    return startsBeforeSlotEnd && endsAfterSlotStart;
  }

  public ScheduleStatus statusForRange(LocalTime rangeStart, LocalTime rangeEnd) {
    return overlaps(rangeStart, rangeEnd) ? ScheduleStatus.IMPOSSIBLE : ScheduleStatus.POSSIBLE;
  }
}
