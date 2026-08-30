package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 확정·종료 시 희망 기간의 멤버 정기+개별 합친 일정을 스냅샷으로 고정한다 (이후 읽기 전용). */
@Service
class TripScheduleSnapshotService {

  private final TripMemberScheduleSnapshotRepository snapshotRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final GoogleCalendarService googleCalendarService;

  private final TripServiceSupport support;

  TripScheduleSnapshotService(
      TripMemberScheduleSnapshotRepository snapshotRepository,
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      GoogleCalendarService googleCalendarService,
      TripServiceSupport support) {
    this.snapshotRepository = snapshotRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.googleCalendarService = googleCalendarService;
    this.support = support;
  }

  // CONFIRMED|EXPIRED 전환과 같은 TX에서 호출 — 이미 freeze된 방은 no-op(idempotent)
  @Transactional
  public void freezeTrip(Trip trip) {
    UUID tripId = trip.getId();
    if (snapshotRepository.existsByTrip_Id(tripId)) {
      return;
    }
    LocalDate startDate = trip.getStartRange();
    LocalDate endDate = trip.getEndRange();
    LocalDateTime frozenAt = LocalDateTime.now();

    List<TripMember> members = support.listActiveMembersSortedByJoinedAt(tripId);
    List<UUID> userIds = members.stream().map(member -> member.getUser().getId()).toList();
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser =
        googleCalendarService.findBusyDaysByUserIds(userIds, startDate, endDate);
    Map<UUID, List<CalendarDayResponse>> resolvedByUser =
        support.resolveMergedSchedules(
            regularScheduleRepository,
            personalScheduleRepository,
            userIds,
            startDate,
            endDate,
            googleBusyByUser);

    List<TripMemberScheduleSnapshot> rows = new ArrayList<>();
    for (TripMember member : members) {
      UUID userId = member.getUser().getId();
      List<CalendarDayResponse> days = resolvedByUser.getOrDefault(userId, List.of());
      for (CalendarDayResponse day : days) {
        rows.add(
            TripMemberScheduleSnapshot.create(
                trip,
                member.getUser(),
                day.date(),
                day.morningStatus(),
                day.afternoonStatus(),
                day.eveningStatus(),
                day.uncertain(),
                frozenAt));
      }
    }
    snapshotRepository.saveAll(rows);
  }
}
