package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.CalendarDay;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.MemberCalendar;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse.TripMemberItemResponse;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TripMemberQueryService {

  private final TripMemberRepository tripMemberRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final TripServiceSupport support;

  TripMemberQueryService(
      TripMemberRepository tripMemberRepository,
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      TripServiceSupport support) {
    this.tripMemberRepository = tripMemberRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.support = support;
  }

  @Transactional(readOnly = true)
  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    support.requireActiveMember(tripId, userId);
    Trip trip = support.requireActiveTrip(tripId);

    List<TripMember> members =
        tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
            .sorted(Comparator.comparing(TripMember::getJoinedAt))
            .toList();

    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    int joinedMemberCount = members.size();
    int respondedCount =
        (int) members.stream()
            .filter(m -> m.getStatus() == TripMemberStatus.RESPONDED)
            .count();
    int memberCount = trip.getMemberCount() == null ? 0 : trip.getMemberCount();
    double memberFillRate = TripServiceSupport.memberFillRate(joinedMemberCount, memberCount);

    List<TripMemberItemResponse> items = new ArrayList<>();
    for (TripMember member : members) {
      items.add(
          new TripMemberItemResponse(
              member.getUser().getId(),
              displayNames.get(member.getUser().getId()),
              member.getRole(),
              member.getStatus(),
              member.isPinned()));
    }

    return new TripMembersResponse(
        memberCount, joinedMemberCount, respondedCount, memberFillRate, items);
  }

  @Transactional(readOnly = true)
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    support.requireActiveMember(tripId, userId);
    Trip trip = support.requireActiveTrip(tripId);
    LocalDate startDate = trip.getStartRange();
    LocalDate endDate = trip.getEndRange();

    List<TripMember> members =
        tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
            .sorted(Comparator.comparing(TripMember::getJoinedAt))
            .toList();

    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    List<MemberCalendar> memberCalendars = new ArrayList<>();
    for (TripMember member : members) {
      UUID memberUserId = member.getUser().getId();
      List<RegularSchedule> regulars =
          regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(memberUserId);
      List<PersonalSchedule> personals =
          personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
              memberUserId,
              startDate,
              endDate);
      List<CalendarDayResponse> resolved =
          ScheduleCalendarResolver.resolve(regulars, personals, startDate, endDate);

      List<CalendarDay> days =
          resolved.stream()
              .map(
                  d -> new CalendarDay(
                      d.date(),
                      d.morningStatus(),
                      d.afternoonStatus(),
                      d.eveningStatus(),
                      d.uncertain()))
              .toList();

      memberCalendars.add(
          new MemberCalendar(
              memberUserId,
              displayNames.get(memberUserId),
              member.getRole(),
              member.getStatus(),
              days));
    }

    return new MemberScheduleCalendarResponse(startDate, endDate, memberCalendars);
  }
}
