package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #38 — CONFIRMED/TERMINATED 시 희망 기간 effective freeze (R-model A · R-gap 공백 불허). */
@Service
public class TripScheduleSnapshotService {

  private final TripMemberRepository tripMemberRepository;

  private final TripMemberScheduleSnapshotRepository snapshotRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  public TripScheduleSnapshotService(
      TripMemberRepository tripMemberRepository,
      TripMemberScheduleSnapshotRepository snapshotRepository,
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository) {
    this.tripMemberRepository = tripMemberRepository;
    this.snapshotRepository = snapshotRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
  }

  // status=CONFIRMED|TERMINATED 전환과 동일 TX에서 호출. 이미 freeze된 trip은 no-op (idempotent)
  @Transactional
  public void freezeTrip(Trip trip) {
    UUID tripId = trip.getId();
    if (snapshotRepository.existsByTrip_Id(tripId)) {
      return;
    }
    LocalDate startDate = trip.getStartRange();
    LocalDate endDate = trip.getEndRange();
    LocalDateTime frozenAt = LocalDateTime.now();

    List<TripMember> members =
        tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
            .sorted(Comparator.comparing(TripMember::getJoinedAt))
            .toList();

    List<TripMemberScheduleSnapshot> rows = new ArrayList<>();
    for (TripMember member : members) {
      UUID userId = member.getUser().getId();
      List<RegularSchedule> regulars =
          regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId);
      List<PersonalSchedule> personals =
          personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
              userId,
              startDate,
              endDate);
      List<CalendarDayResponse> days =
          ScheduleCalendarResolver.resolve(regulars, personals, startDate, endDate);
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
