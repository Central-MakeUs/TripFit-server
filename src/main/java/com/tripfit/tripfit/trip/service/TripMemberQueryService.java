package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.CalendarDay;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.MemberCalendar;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse.TripMemberItemResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripMemberScheduleSnapshotRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 여행방 멤버 목록·멤버별 일정 달력 조회
class TripMemberQueryService {

  private final TripMemberRepository tripMemberRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final TripMemberScheduleSnapshotRepository snapshotRepository;

  private final TripServiceSupport support;

  TripMemberQueryService(
      TripMemberRepository tripMemberRepository,
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      TripMemberScheduleSnapshotRepository snapshotRepository,
      TripServiceSupport support) {
    this.tripMemberRepository = tripMemberRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.snapshotRepository = snapshotRepository;
    this.support = support;
  }

  // 멤버 목록 조회 — 모집률·동명이인 displayName 포함
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

  // 희망 기간 멤버 전원 일정 달력 — 조율 중은 실시간, 확정·종료는 스냅샷(읽기 전용). 취소 방은 거부
  @Transactional(readOnly = true)
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    support.requireActiveMember(tripId, userId);
    Trip trip = support.requireActiveTrip(tripId);
    TripStatus status = support.effectiveStatus(trip);
    if (status == TripStatus.CANCELED) {
      throw new TripFitException(TripErrorCode.TRIP_CANCELED);
    }

    LocalDate startDate = trip.getStartRange();
    LocalDate endDate = trip.getEndRange();
    List<TripMember> members =
        tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
            .sorted(Comparator.comparing(TripMember::getJoinedAt))
            .toList();
    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    boolean readOnly = status == TripStatus.CONFIRMED || status == TripStatus.TERMINATED;
    List<MemberCalendar> memberCalendars =
        readOnly
            ? buildFromSnapshots(tripId, members, displayNames)
            : buildLive(members, displayNames, startDate, endDate);

    return new MemberScheduleCalendarResponse(startDate, endDate, readOnly, memberCalendars);
  }

  // 조율 중(ONGOING) — 정기·개인 일정을 합쳐 effective 달력 생성
  private List<MemberCalendar> buildLive(
      List<TripMember> members,
      Map<UUID, String> displayNames,
      LocalDate startDate,
      LocalDate endDate) {
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
    return memberCalendars;
  }

  // 확정·종료 — 저장해 둔 스냅샷 row로 달력 구성
  private List<MemberCalendar> buildFromSnapshots(
      UUID tripId,
      List<TripMember> members,
      Map<UUID, String> displayNames) {
    Map<UUID, List<TripMemberScheduleSnapshot>> byUser =
        snapshotRepository.findByTrip_IdOrderByUser_IdAscScheduleDateAsc(tripId).stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.getUser().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()));

    List<MemberCalendar> memberCalendars = new ArrayList<>();
    for (TripMember member : members) {
      UUID memberUserId = member.getUser().getId();
      List<CalendarDay> days =
          byUser.getOrDefault(memberUserId, List.of()).stream()
              .map(
                  s -> {
                    SlotStatuses slots = s.getSlotStatuses();
                    return new CalendarDay(
                        s.getScheduleDate(),
                        slots.getMorningStatus(),
                        slots.getAfternoonStatus(),
                        slots.getEveningStatus(),
                        s.isUncertain());
                  })
              .toList();
      memberCalendars.add(
          new MemberCalendar(
              memberUserId,
              displayNames.get(memberUserId),
              member.getRole(),
              member.getStatus(),
              days));
    }
    return memberCalendars;
  }
}
