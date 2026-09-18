package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// trip이 필요로 하는 정기·개별 일정 조회를 이 도메인(user.schedule)의 repository·ScheduleCalendarResolver를
// 감싸서 제공한다 — trip 쪽 여러 서비스가 각자 repository를 중복 조회하지 않도록 이 클래스 하나로 모은다.
@Component
public class ScheduleAvailabilityService {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserRepository userRepository;

  private final HolidayProvider holidayProvider;

  private final GoogleCalendarService googleCalendarService;

  public ScheduleAvailabilityService(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      UserRepository userRepository,
      HolidayProvider holidayProvider,
      GoogleCalendarService googleCalendarService) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.userRepository = userRepository;
    this.holidayProvider = holidayProvider;
    this.googleCalendarService = googleCalendarService;
  }

  // userId 목록으로 정기 일정을 한 번에 조회한 뒤 userId별로 묶어서 반환한다(N+1 방지 — 사용자 수만큼
  // 반복 쿼리하지 않음). resolveMergedSchedules도 내부에서 이 메서드를 재사용한다.
  public Map<UUID, List<RegularSchedule>> findRegularSchedulesByUserIds(List<UUID> userIds) {
    return regularScheduleRepository.findByUserIdIn(userIds).stream()
        .collect(Collectors.groupingBy(regular -> regular.getUser().getId()));
  }

  // userId 목록·기간으로 개별 일정을 한 번에 조회한 뒤 userId별로 묶어서 반환한다(N+1 방지).
  // resolveMergedSchedules도 내부에서 이 메서드를 재사용한다.
  public Map<UUID, List<PersonalSchedule>> findPersonalSchedulesByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    return personalScheduleRepository
        .findByUserIdInAndScheduleDateBetween(userIds, startDate, endDate).stream()
        .collect(Collectors.groupingBy(personal -> personal.getUser().getId()));
  }

  // 구글 busy 조회 → 정기·개별 일정과 병합 순서를 이 메서드 안에 캡슐화해, 호출부가 순서를 지킬 필요가 없게
  // 한다(과거엔 trip 쪽 3곳이 이 2단계 호출을 각자 복제해서, 순서를 어기면 구글 busy가 조용히 무시될 위험이
  // 있었다). 원본 busy 맵도 함께 반환하므로, merge 결과 외에 원본 busy 정보가 따로 필요한 호출부
  // (RecommendationEngine의 연차 시뮬레이션)도 이 메서드 하나로 충분하다.
  public ScheduleAvailability resolveAvailability(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser =
        googleCalendarService.findBusyDaysByUserIds(userIds, startDate, endDate);
    Map<UUID, List<CalendarDayResponse>> mergedByUser =
        resolveMergedSchedules(userIds, startDate, endDate, googleBusyByUser);
    return new ScheduleAvailability(googleBusyByUser, mergedByUser);
  }

  // 정기+개별 일정을 로드해 구글 busy와 합친 달력으로 만든다 — live 조회·snapshot freeze·추천 후보 계산 공용.
  // 멤버 목록을 배치 조회해 멤버 수만큼 반복 쿼리하지 않게 함(N+1 방지). busy를 이미 별도로 들고 있을 때만 직접
  // 쓰고, 그 외에는 resolveAvailability를 쓴다(순서 보장이 그쪽에 캡슐화돼 있음).
  private Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser) {
    Map<UUID, List<RegularSchedule>> regularsByUser = findRegularSchedulesByUserIds(userIds);
    Map<UUID, List<PersonalSchedule>> personalsByUser =
        findPersonalSchedulesByUserIds(userIds, startDate, endDate);
    // 공휴일 휴무(User) 배치 조회 — 멤버 수만큼 반복 쿼리하지 않음(N+1 방지)
    Map<UUID, User> usersByUser =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    // 공휴일은 전 사용자 공통이라 멤버 수와 무관하게 한 번만 조회
    Set<LocalDate> holidays = holidayProvider.findHolidaysBetween(startDate, endDate);

    Map<UUID, List<CalendarDayResponse>> byUser = new HashMap<>();
    for (UUID userId : userIds) {
      User user = usersByUser.get(userId);
      byUser.put(
          userId,
          ScheduleCalendarResolver.resolve(
              regularsByUser.getOrDefault(userId, List.of()),
              personalsByUser.getOrDefault(userId, List.of()),
              startDate,
              endDate,
              googleBusyByUser.getOrDefault(userId, Map.of()),
              holidays,
              user != null && user.isHolidayRest()));
    }
    return byUser;
  }

  // resolveAvailability 반환값 — 원본 구글 busy 맵과 병합된 최종 달력을 함께 담는다
  public record ScheduleAvailability(
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser,
      Map<UUID, List<CalendarDayResponse>> mergedByUser
  ) {
  }
}
