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

// 정기 요일 펼침 ⊕ 구글 busy(자동 계산 기본값)를, 개별 일정의 슬롯 단위 오버라이드로 덮어써 날짜별 최종 슬롯을 만든다(O1)
// 슬롯 3개+구글 신호가 전부 없는 날짜는 응답에서 omit(sparse)
public final class ScheduleCalendarResolver {

  private ScheduleCalendarResolver() {}

  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate) {
    return resolve(regulars, personals, startDate, endDate, Map.of());
  }

  // 1. 날짜별 정기 매칭 2. 슬롯마다 정기⊕구글로 기본값 계산 3. 개별 오버라이드가 있으면 그 슬롯만 최종 승자 — 개별·정기·구글 전부 없는 날짜만 omit
  public static List<CalendarDayResponse> resolve(
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate) {
    Map<LocalDate, CalendarDayResponse> byDate = new HashMap<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      PersonalSchedule personal = findPersonal(personals, date);
      List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
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

  private static PersonalSchedule findPersonal(List<PersonalSchedule> personals, LocalDate date) {
    for (PersonalSchedule personal : personals) {
      if (personal.getScheduleDate().equals(date)) {
        return personal;
      }
    }
    return null;
  }

  // 하루치 최종 슬롯 조립 — 슬롯마다 resolveSlot을 적용하고 uncertain은 개별 존재 여부로만 결정
  private static CalendarDayResponse resolveDay(
      LocalDate date,
      List<RegularSchedule> matched,
      PersonalSchedule personal,
      GoogleCalendarBusyDay googleBusy) {
    SlotStatuses regular = combineImpossibleWins(matched);
    SlotStatuses override = personal != null ? personal.getSlotStatuses() : SlotStatuses.empty();
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

  // 슬롯 하나의 최종값 = 개별 오버라이드(있으면) > 정기⊕구글(자동 계산) > POSSIBLE("미입력≠불가능")
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

  // 정기 슬롯값과 구글 busy를 OR 병합 — 정기 IMPOSSIBLE이거나 구글 busy면 IMPOSSIBLE, 둘 다 신호 없으면 null
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

  // 같은 요일 정기 일정들의 슬롯을 IMPOSSIBLE 우선으로 합침 — 추천 엔진의 정기-only(연차 필요 판정) 재사용을 위해 public
  public static SlotStatuses combineImpossibleWins(List<RegularSchedule> matched) {
    return new SlotStatuses(
        mergeSlot(matched, true, false, false),
        mergeSlot(matched, false, true, false),
        mergeSlot(matched, false, false, true));
  }

  // daysOfWeek 문자열에 해당 요일이 포함되는지 판별함 — 추천 엔진 재사용을 위해 public
  public static boolean matchesDayOfWeek(String daysOfWeek, DayOfWeek dayOfWeek) {
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
}
