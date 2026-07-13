package com.tripfit.tripfit.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeSlotTest {

  @Test
  void nineToSix_morningAfternoonImpossible_eveningPossible() {
    LocalTime start = LocalTime.of(9, 0);
    LocalTime end = LocalTime.of(18, 0);

    assertThat(TimeSlot.MORNING.statusForRange(start, end)).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(TimeSlot.AFTERNOON.statusForRange(start, end)).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(TimeSlot.EVENING.statusForRange(start, end)).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void eveningOnly_ninePmToEleven_onlyEveningImpossible() {
    LocalTime start = LocalTime.of(21, 0);
    LocalTime end = LocalTime.of(23, 0);

    assertThat(TimeSlot.MORNING.statusForRange(start, end)).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(TimeSlot.AFTERNOON.statusForRange(start, end)).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(TimeSlot.EVENING.statusForRange(start, end)).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void boundary_endsExactlyAtSlotStart_noOverlap() {
    // [09:00, 13:00) does not overlap afternoon [13:00, 18:00)
    assertThat(TimeSlot.AFTERNOON.overlaps(LocalTime.of(9, 0), LocalTime.of(13, 0))).isFalse();
    assertThat(TimeSlot.MORNING.overlaps(LocalTime.of(9, 0), LocalTime.of(13, 0))).isTrue();
  }
}
