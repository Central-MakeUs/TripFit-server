package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.Weekday;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 정기 요일 펼침 + 개별 일정 덮어쓰기 → 날짜별 합산 슬롯. 일정 없는 날짜는 omit(sparse)
public final class ScheduleCalendarResolver {

  private ScheduleCalendarResolver() {}

  // 1. personal 있으면 그날 슬롯 그대로 2. 없으면 regular 합침(IMPOSSIBLE 우선) 3. Google busy OR 병합 4. 전부 null이면 날짜
  // 생략
  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate) {
    return resolve(regulars, personals, startDate, endDate, Map.of());
  }

  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate) {
    Map<LocalDate, CalendarDayResponse> byDate = new HashMap<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      PersonalSchedule personal = findPersonal(personals, date);
      CalendarDayResponse manualDay = resolveManualDay(regulars, personal, date);
      GoogleCalendarBusyDay googleBusy =
          googleBusyByDate != null ? googleBusyByDate.get(date) : null;
      if (manualDay == null && googleBusy == null) {
        continue;
      }
      if (manualDay == null) {
        byDate.put(date, googleOnlyDay(date, googleBusy));
      } else if (googleBusy == null) {
        byDate.put(date, manualDay);
      } else {
        byDate.put(date, mergeWithGoogle(manualDay, googleBusy));
      }
    }
    return byDate.values().stream()
        .sorted(Comparator.comparing(CalendarDayResponse::date))
        .toList();
  }

  private static PersonalSchedule findPersonal(List<PersonalSchedule> personals, LocalDate date) {
    for (PersonalSchedule personal : personals) {
      if (personal.getScheduleDate().equals(date)) {
        return personal;
      }
    }
    return null;
  }

  private static CalendarDayResponse resolveManualDay(
      List<RegularSchedule> regulars,
      PersonalSchedule personal,
      LocalDate date) {
    if (personal != null) {
      SlotStatuses slots = personal.getSlotStatuses();
      return new CalendarDayResponse(
          date,
          slots.getMorningStatus(),
          slots.getAfternoonStatus(),
          slots.getEveningStatus(),
          personal.isUncertain());
    }

    List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
    if (matched.isEmpty()) {
      return null;
    }
    SlotStatuses combined = combineImpossibleWins(matched);
    if (combined.getMorningStatus() == null
        && combined.getAfternoonStatus() == null
        && combined.getEveningStatus() == null) {
      return null;
    }
    return new CalendarDayResponse(
        date,
        nullToPossible(combined.getMorningStatus()),
        nullToPossible(combined.getAfternoonStatus()),
        nullToPossible(combined.getEveningStatus()),
        false);
  }

  private static CalendarDayResponse googleOnlyDay(
      LocalDate date,
      GoogleCalendarBusyDay googleBusy) {
    return new CalendarDayResponse(
        date,
        googleBusy.isMorningBusy() ? ScheduleStatus.IMPOSSIBLE : ScheduleStatus.POSSIBLE,
        googleBusy.isAfternoonBusy() ? ScheduleStatus.IMPOSSIBLE : ScheduleStatus.POSSIBLE,
        googleBusy.isEveningBusy() ? ScheduleStatus.IMPOSSIBLE : ScheduleStatus.POSSIBLE,
        false);
  }

  private static CalendarDayResponse mergeWithGoogle(
      CalendarDayResponse manual,
      GoogleCalendarBusyDay googleBusy) {
    return new CalendarDayResponse(
        manual.date(),
        orImpossible(manual.morningStatus(), googleBusy.isMorningBusy()),
        orImpossible(manual.afternoonStatus(), googleBusy.isAfternoonBusy()),
        orImpossible(manual.eveningStatus(), googleBusy.isEveningBusy()),
        manual.uncertain());
  }

  private static ScheduleStatus orImpossible(ScheduleStatus manual, boolean googleBusy) {
    if (manual == ScheduleStatus.IMPOSSIBLE || googleBusy) {
      return ScheduleStatus.IMPOSSIBLE;
    }
    return ScheduleStatus.POSSIBLE;
  }

  // 같은 요일 정기 일정들의 슬롯을 IMPOSSIBLE 우선으로 합침
  static SlotStatuses combineImpossibleWins(List<RegularSchedule> matched) {
    return new SlotStatuses(
        mergeSlot(matched, true, false, false),
        mergeSlot(matched, false, true, false),
        mergeSlot(matched, false, false, true));
  }

  // daysOfWeek 문자열에 해당 요일이 포함되는지 판별함
  static boolean matchesDayOfWeek(String daysOfWeek, DayOfWeek dayOfWeek) {
    return parseDaysOfWeek(daysOfWeek).contains(dayOfWeek);
  }

  // "MON,TUE,WED" 형태를 DayOfWeek 집합으로 파싱함 (잘못된 토큰은 스킵 — 저장본 호환)
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

  private static List<RegularSchedule> matchingRegulars(
      List<RegularSchedule> regulars,
      DayOfWeek dayOfWeek) {
    List<RegularSchedule> matched = new ArrayList<>();
    for (RegularSchedule regular : regulars) {
      if (matchesDayOfWeek(regular.getDaysOfWeek(), dayOfWeek)) {
        matched.add(regular);
      }
    }
    return matched;
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

  // merge 결과가 null인 슬롯 → POSSIBLE (미입력 ≠ IMPOSSIBLE)
  private static ScheduleStatus nullToPossible(ScheduleStatus status) {
    return status != null ? status : ScheduleStatus.POSSIBLE;
  }
}
