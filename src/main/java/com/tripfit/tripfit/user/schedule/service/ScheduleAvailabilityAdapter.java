package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// trip.port.out.SchedulePort 구현체 — trip이 정기·개별 일정을 조회할 수 있게, 이 도메인(user.schedule)의
// repository·ScheduleCalendarResolver를 감싸서 노출한다.
//
// trip 패키지는 이 클래스의 존재 자체를 모른다 — Spring이 SchedulePort 타입으로 이 빈을 주입해줄 뿐이다.
// 로직은 전부 기존 코드(RegularScheduleRepository·PersonalScheduleRepository·ScheduleCalendarResolver)
// 그대로이고, 이 어댑터는 "trip이 원하는 시그니처로 다시 포장"하는 역할만 한다 — 즉 여기서 새 비즈니스
// 로직을 추가하면 안 되고, 계산·정책 변경은 항상 원래 주인인 user.schedule의 코드(ScheduleCalendarResolver
// 등)에서 해야 한다.
@Component
public class ScheduleAvailabilityAdapter implements SchedulePort {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  public ScheduleAvailabilityAdapter(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
  }

  // userId 목록으로 정기 일정을 한 번에 조회한 뒤 userId별로 묶어서 반환한다(N+1 방지 — 사용자 수만큼
  // 반복 쿼리하지 않음). resolveMergedSchedules도 내부에서 이 메서드를 재사용한다.
  @Override
  public Map<UUID, List<RegularSchedule>> findRegularSchedulesByUserIds(List<UUID> userIds) {
    return regularScheduleRepository.findByUserIdIn(userIds).stream()
        .collect(Collectors.groupingBy(regular -> regular.getUser().getId()));
  }

  // 정기+개별 일정을 로드해 합친 달력으로 만든다 — live 조회·snapshot freeze·추천 후보 계산 공용. 멤버 목록을 배치
  // 조회해 멤버 수만큼 반복 쿼리하지 않게 함(N+1 방지)
  @Override
  public Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser) {
    Map<UUID, List<RegularSchedule>> regularsByUser = findRegularSchedulesByUserIds(userIds);
    Map<UUID, List<PersonalSchedule>> personalsByUser =
        personalScheduleRepository
            .findByUserIdInAndScheduleDateBetween(userIds, startDate, endDate).stream()
            .collect(Collectors.groupingBy(personal -> personal.getUser().getId()));

    Map<UUID, List<CalendarDayResponse>> byUser = new HashMap<>();
    for (UUID userId : userIds) {
      byUser.put(
          userId,
          ScheduleCalendarResolver.resolve(
              regularsByUser.getOrDefault(userId, List.of()),
              personalsByUser.getOrDefault(userId, List.of()),
              startDate,
              endDate,
              googleBusyByUser.getOrDefault(userId, Map.of())));
    }
    return byUser;
  }
}
