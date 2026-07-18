package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.Weekday;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 정기 요일 펼침 + 개별 일정 덮어쓰기 → 날짜별 합산 슬롯. 일정 없는 날짜는 omit(sparse)
public final class ScheduleCalendarResolver {

  private ScheduleCalendarResolver() {}

  // 1. personal 있으면 그날 슬롯 그대로 2. 없으면 regular 합침(IMPOSSIBLE 우선) 3. 전부 null이면 날짜 생략
  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate) {
    Map<LocalDate, PersonalSchedule> personalByDate = new HashMap<>();
    for (PersonalSchedule personal : personals) {
      personalByDate.put(personal.getScheduleDate(), personal);
    }

    List<CalendarDayResponse> days = new ArrayList<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      PersonalSchedule personal = personalByDate.get(date);
      if (personal != null) {
        SlotStatuses slots = personal.getSlotStatuses();
        days.add(
            new CalendarDayResponse(
                date,
                slots.getMorningStatus(),
                slots.getAfternoonStatus(),
                slots.getEveningStatus(),
                personal.isUncertain()));
        continue;
      }

      List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
      if (matched.isEmpty()) {
        continue;
      }
      SlotStatuses combined = combineImpossibleWins(matched);
      if (combined.getMorningStatus() == null
          && combined.getAfternoonStatus() == null
          && combined.getEveningStatus() == null) {
        continue;
      }
      // regular에서 슬롯이 null이면 API에는 POSSIBLE로 노출
      days.add(
          new CalendarDayResponse(
              date,
              nullToPossible(combined.getMorningStatus()),
              nullToPossible(combined.getAfternoonStatus()),
              nullToPossible(combined.getEveningStatus()),
              false));
    }
    return days;
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
