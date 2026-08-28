package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.schedule.domain.SlotStatuses;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.Weekday;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScheduleCalendarResolver {

  private ScheduleCalendarResolver() {}

  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays,
      boolean holidayRest) {
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      dates.add(date);
    }
    return resolve(regulars, personals, dates, googleBusyByDate, holidays, holidayRest);
  }

  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      Collection<LocalDate> dates,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays,
      boolean holidayRest) {
    Map<LocalDate, PersonalSchedule> personalsByDate = indexByDate(personals);
    Map<DayOfWeek, List<RegularSchedule>> regularsByDayOfWeek = groupByDayOfWeek(regulars);

    Map<LocalDate, CalendarDayResponse> byDate = new HashMap<>();
    for (LocalDate date : dates) {
      PersonalSchedule personal = personalsByDate.get(date);
      List<RegularSchedule> matched =
          regularsAppliedOn(date, regularsByDayOfWeek, holidayRest, holidays);
      GoogleCalendarBusyDay googleBusy =
          googleBusyByDate != null ? googleBusyByDate.get(date) : null;
      if (personal == null && matched.isEmpty() && googleBusy == null) {
        continue;
      }
      byDate.put(date, resolveDay(date, matched, personal, googleBusy));
    }
    return byDate.values().stream()
        .sorted(Comparator.comparing(CalendarDayResponse::date))
        .toList();
  }

  private static List<RegularSchedule> regularsAppliedOn(
      LocalDate date,
      Map<DayOfWeek, List<RegularSchedule>> regularsByDayOfWeek,
      boolean restsOnHolidays,
      Set<LocalDate> holidays) {
    if (restsOnHolidays && holidays != null && holidays.contains(date)) {
      return List.of();
    }
    return regularsByDayOfWeek.getOrDefault(date.getDayOfWeek(), List.of());
  }

  private static Map<LocalDate, PersonalSchedule> indexByDate(List<PersonalSchedule> personals) {
    Map<LocalDate, PersonalSchedule> byDate = new HashMap<>();
    for (PersonalSchedule personal : personals) {
      byDate.put(personal.getScheduleDate(), personal);
    }
    return byDate;
  }

  private static Map<DayOfWeek, List<RegularSchedule>> groupByDayOfWeek(
      List<RegularSchedule> regulars) {
    Map<DayOfWeek, List<RegularSchedule>> byDayOfWeek = new HashMap<>();
    for (RegularSchedule regular : regulars) {
      for (DayOfWeek dayOfWeek : parseDaysOfWeek(regular.getDaysOfWeek())) {
        byDayOfWeek.computeIfAbsent(dayOfWeek, key -> new ArrayList<>()).add(regular);
      }
    }
    return byDayOfWeek;
  }

  private static CalendarDayResponse resolveDay(
      LocalDate date,
      List<RegularSchedule> matched,
      PersonalSchedule personal,
      GoogleCalendarBusyDay googleBusy) {
    SlotStatuses regular = combineImpossibleWins(matched);

    SlotStatuses override =
        personal != null && personal.getSlotStatuses() != null
            ? personal.getSlotStatuses()
            : SlotStatuses.empty();
    return new CalendarDayResponse(
        date,
        resolveSlot(
            regular.getMorningStatus(),
            busyOrNull(googleBusy, true, false, false),
            override.getMorningStatus()),
        resolveSlot(
            regular.getAfternoonStatus(),
            busyOrNull(googleBusy, false, true, false),
            override.getAfternoonStatus()),
        resolveSlot(
            regular.getEveningStatus(),
            busyOrNull(googleBusy, false, false, true),
            override.getEveningStatus()),
        personal != null && personal.isUncertain());
  }

  private static ScheduleStatus resolveSlot(
      ScheduleStatus regularSlot,
      Boolean googleBusy,
      ScheduleStatus override) {
    if (override != null) {
      return override;
    }
    ScheduleStatus base = combineWithGoogle(regularSlot, googleBusy);
    return base != null ? base : ScheduleStatus.POSSIBLE;
  }

  private static ScheduleStatus combineWithGoogle(ScheduleStatus regularSlot, Boolean googleBusy) {
    if (regularSlot == ScheduleStatus.IMPOSSIBLE || Boolean.TRUE.equals(googleBusy)) {
      return ScheduleStatus.IMPOSSIBLE;
    }
    if (regularSlot == ScheduleStatus.POSSIBLE || Boolean.FALSE.equals(googleBusy)) {
      return ScheduleStatus.POSSIBLE;
    }
    return null;
  }

  private static Boolean busyOrNull(
      GoogleCalendarBusyDay googleBusy,
      boolean morning,
      boolean afternoon,
      boolean evening) {
    if (googleBusy == null) {
      return null;
    }
    return morning ? googleBusy.isMorningBusy()
        : afternoon ? googleBusy.isAfternoonBusy() : googleBusy.isEveningBusy();
  }

  public static SlotStatuses combineImpossibleWins(List<RegularSchedule> matched) {
    return new SlotStatuses(
        mergeSlot(matched, true, false, false),
        mergeSlot(matched, false, true, false),
        mergeSlot(matched, false, false, true));
  }

  public static boolean matchesDayOfWeek(String daysOfWeek, DayOfWeek dayOfWeek) {
    return parseDaysOfWeek(daysOfWeek).contains(dayOfWeek);
  }

  static Set<DayOfWeek> parseDaysOfWeek(String daysOfWeek) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    if (daysOfWeek == null || daysOfWeek.isBlank()) {
      return days;
    }
    for (String token : daysOfWeek.split(",")) {
      Weekday weekday = Weekday.fromToken(token);
      if (weekday != null) {
        days.add(weekday.toDayOfWeek());
      }
    }
    return days;
  }

  private static ScheduleStatus mergeSlot(
      List<RegularSchedule> matched,
      boolean morning,
      boolean afternoon,
      boolean evening) {
    boolean sawPossible = false;
    for (RegularSchedule regular : matched) {
      SlotStatuses slots = regular.getSlotStatuses();
      if (slots == null) {
        continue;
      }
      ScheduleStatus status =
          morning
              ? slots.getMorningStatus()
              : afternoon ? slots.getAfternoonStatus() : slots.getEveningStatus();
      if (status == ScheduleStatus.IMPOSSIBLE) {
        return ScheduleStatus.IMPOSSIBLE;
      }
      if (status == ScheduleStatus.POSSIBLE) {
        sawPossible = true;
      }
    }
    return sawPossible ? ScheduleStatus.POSSIBLE : null;
  }
}
