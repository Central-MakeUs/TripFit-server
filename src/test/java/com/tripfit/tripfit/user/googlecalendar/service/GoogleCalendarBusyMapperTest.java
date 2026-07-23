package com.tripfit.tripfit.user.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleCalendarBusyMapperTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Test
  void mapIntervalsToDays_morningMeeting_marksMorningBusy() {
    LocalDate date = LocalDate.of(2026, 8, 3);
    Instant start = date.atTime(9, 0).atZone(SEOUL).toInstant();
    Instant end = date.atTime(12, 0).atZone(SEOUL).toInstant();

    Map<LocalDate, GoogleCalendarBusyMapper.SlotBusyFlags> mapped =
        GoogleCalendarBusyMapper.mapIntervalsToDays(
            List.of(new GoogleFreeBusyInterval(start, end)));

    assertThat(mapped).containsKey(date);
    assertThat(mapped.get(date).isMorningBusy()).isTrue();
    assertThat(mapped.get(date).isAfternoonBusy()).isFalse();
    assertThat(mapped.get(date).isEveningBusy()).isFalse();
  }

  @Test
  void mapIntervalsToDays_allDayBusy_marksAllSlots() {
    LocalDate date = LocalDate.of(2026, 8, 4);
    Instant start = date.atStartOfDay(SEOUL).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(SEOUL).toInstant();

    Map<LocalDate, GoogleCalendarBusyMapper.SlotBusyFlags> mapped =
        GoogleCalendarBusyMapper.mapIntervalsToDays(
            List.of(new GoogleFreeBusyInterval(start, end)));

    assertThat(mapped.get(date).isMorningBusy()).isTrue();
    assertThat(mapped.get(date).isAfternoonBusy()).isTrue();
    assertThat(mapped.get(date).isEveningBusy()).isTrue();
  }
}
