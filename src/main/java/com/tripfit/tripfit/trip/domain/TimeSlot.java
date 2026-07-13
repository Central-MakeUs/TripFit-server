package com.tripfit.tripfit.trip.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "일정 입력 시간대 (반개구간). 정기·개별 일정 슬롯 계산에 공통 사용")
public enum TimeSlot {
  @Schema(description = "오전 [00:00, 13:00)")
  MORNING(LocalTime.MIDNIGHT, LocalTime.of(13, 0)),

  @Schema(description = "오후 [13:00, 18:00)")
  AFTERNOON(LocalTime.of(13, 0), LocalTime.of(18, 0)),

  @Schema(description = "저녁 [18:00, 24:00)")
  EVENING(LocalTime.of(18, 0), null);

  private final LocalTime startInclusive;

  // null = 24:00 (하루 끝, exclusive)
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

  // [rangeStart, rangeEnd) 와 이 슬롯이 겹치면 true
  public boolean overlaps(LocalTime rangeStart, LocalTime rangeEnd) {
    if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
      return false;
    }
    boolean startsBeforeSlotEnd =
        endExclusive == null || rangeStart.isBefore(endExclusive);
    boolean endsAfterSlotStart = rangeEnd.isAfter(startInclusive);
    return startsBeforeSlotEnd && endsAfterSlotStart;
  }

  // 구간과 겹치면 IMPOSSIBLE, 아니면 POSSIBLE
  public ScheduleStatus statusForRange(LocalTime rangeStart, LocalTime rangeEnd) {
    return overlaps(rangeStart, rangeEnd) ? ScheduleStatus.IMPOSSIBLE : ScheduleStatus.POSSIBLE;
  }
}
