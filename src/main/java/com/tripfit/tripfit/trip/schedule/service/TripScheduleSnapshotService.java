package com.tripfit.tripfit.trip.schedule.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripScheduleSnapshotService {

  private final TripMemberScheduleSnapshotRepository snapshotRepository;

  private final ScheduleAvailabilityService scheduleAvailabilityService;

  private final TripServiceSupport support;

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
    Map<UUID, List<CalendarDayResponse>> resolvedByUser =
        scheduleAvailabilityService.resolveAvailability(userIds, startDate, endDate).mergedByUser();

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
