package com.tripfit.tripfit.user.googlecalendar.service;

import com.tripfit.tripfit.trip.domain.TimeSlot;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GoogleCalendarBusyMapper {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private GoogleCalendarBusyMapper() {}

  // freeBusy busy[] → 날짜×슬롯 boolean (busy 슬롯 있는 날만 sparse)
  public static Map<LocalDate, SlotBusyFlags> mapIntervalsToDays(
      List<GoogleFreeBusyInterval> intervals) {
    Map<LocalDate, SlotBusyFlags> byDate = new HashMap<>();
    for (GoogleFreeBusyInterval interval : intervals) {
      applyInterval(byDate, interval);
    }
    byDate.entrySet().removeIf(entry -> !entry.getValue().hasAnyBusy());
    return byDate;
  }

  public static GoogleCalendarBusyDay toEntity(
      User user,
      LocalDate scheduleDate,
      SlotBusyFlags flags) {
    return GoogleCalendarBusyDay.create(
        user,
        scheduleDate,
        flags.isMorningBusy(),
        flags.isAfternoonBusy(),
        flags.isEveningBusy());
  }

  private static void applyInterval(
      Map<LocalDate, SlotBusyFlags> byDate,
      GoogleFreeBusyInterval interval) {
    ZonedDateTime start = interval.start().atZone(SEOUL);
    ZonedDateTime end = interval.end().atZone(SEOUL);
    if (!end.isAfter(start)) {
      return;
    }

    LocalDate date = start.toLocalDate();
    LocalDate endDate = end.toLocalDate();
    if (end.toLocalTime().equals(LocalTime.MIDNIGHT) && end.isAfter(start) && !end.equals(start)) {
      endDate = endDate.minusDays(1);
    }

    while (!date.isAfter(endDate)) {
      LocalTime dayStart =
          date.equals(start.toLocalDate()) ? start.toLocalTime() : LocalTime.MIDNIGHT;
      LocalTime dayEnd =
          date.equals(end.toLocalDate()) ? end.toLocalTime()
              : LocalTime.of(23, 59, 59, 999_999_999);
      if (date.equals(end.toLocalDate()) && dayEnd.equals(LocalTime.MIDNIGHT)) {
        dayEnd = LocalTime.of(23, 59, 59, 999_999_999);
      }
      SlotBusyFlags flags = byDate.computeIfAbsent(date, ignored -> new SlotBusyFlags());
      flags.mergeDayRange(dayStart, dayEnd);
      date = date.plusDays(1);
    }
  }

  public static final class SlotBusyFlags {

    private boolean morningBusy;

    private boolean afternoonBusy;

    private boolean eveningBusy;

    public boolean isMorningBusy() {
      return morningBusy;
    }

    public boolean isAfternoonBusy() {
      return afternoonBusy;
    }

    public boolean isEveningBusy() {
      return eveningBusy;
    }

    public boolean hasAnyBusy() {
      return morningBusy || afternoonBusy || eveningBusy;
    }

    public void mergeDayRange(LocalTime rangeStart, LocalTime rangeEnd) {
      if (TimeSlot.MORNING.overlaps(rangeStart, rangeEnd)) {
        morningBusy = true;
      }
      if (TimeSlot.AFTERNOON.overlaps(rangeStart, rangeEnd)) {
        afternoonBusy = true;
      }
      if (TimeSlot.EVENING.overlaps(rangeStart, rangeEnd)) {
        eveningBusy = true;
      }
    }
  }
}
