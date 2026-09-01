package com.tripfit.tripfit.trip.membership.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.trip.service.TripDisplayNameHelper;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import com.tripfit.tripfit.trip.schedule.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse.CalendarDay;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse.MemberCalendar;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse.TripMemberItemResponse;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class TripMemberQueryService {

  private final TripMemberScheduleSnapshotRepository snapshotRepository;

  private final TripServiceSupport support;

  private final ScheduleAvailabilityService scheduleAvailabilityService;

  // 여행방에 속한 멤버 목록을 조회합니다.
  // 1. 참여 순으로 활성 멤버 목록 정렬 및 동명이인 처리(디스플레이 이름 할당)
  // 2. 전체 인원 대비 실제 참여 인원 비율(Fill Rate) 계산
  @Transactional(readOnly = true)
  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    support.requireMembership(tripId, userId);
    Trip trip = support.requireActiveTrip(tripId);

    List<TripMember> members = support.listActiveMembersSortedByJoinedAt(tripId);

    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    int activeMemberCount =
        (int) members.stream()
            .filter(m -> m.getStatus() == TripMemberStatus.ACTIVE)
            .count();
    int memberCount = trip.getMemberCount() == null ? 0 : trip.getMemberCount();
    double memberFillRate = TripServiceSupport.memberFillRate(activeMemberCount, memberCount);

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

    return new TripMembersResponse(memberCount, activeMemberCount, memberFillRate, items);
  }

  // 여행방 멤버들의 통합 일정표(캘린더)를 조회합니다.
  @Transactional(readOnly = true)
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    support.requireMembership(tripId, userId);
    Trip trip = support.requireActiveTrip(tripId);
    TripStatus status = support.effectiveStatus(trip);

    LocalDate startDate = trip.getStartRange();
    LocalDate endDate = trip.getEndRange();
    List<TripMember> members = support.listActiveMembersSortedByJoinedAt(tripId);
    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    // 1. 현재 여행방 상태가 확정(CONFIRMED)이거나 만료(EXPIRED)된 경우, 더 이상 일정이 변경되지 않으므로 DB 스냅샷(과거 기록)을 읽어옵니다.
    // 2. 진행 중(ONGOING)인 경우 실시간으로 연동된 구글 캘린더, 정기 일정, 개별 일정을 병합 연산하여 보여줍니다.
    boolean readOnly = status == TripStatus.CONFIRMED || status == TripStatus.EXPIRED;
    List<MemberCalendar> memberCalendars =
        readOnly
            ? buildFromSnapshots(tripId, members, displayNames)
            : buildLive(members, displayNames, startDate, endDate);

    return new MemberScheduleCalendarResponse(startDate, endDate, readOnly, memberCalendars);
  }

  private List<MemberCalendar> buildLive(
      List<TripMember> members,
      Map<UUID, String> displayNames,
      LocalDate startDate,
      LocalDate endDate) {
    List<UUID> memberUserIds = members.stream().map(m -> m.getUser().getId()).toList();
    Map<UUID, List<CalendarDayResponse>> resolvedByUser =
        scheduleAvailabilityService.resolveAvailability(memberUserIds, startDate, endDate)
            .mergedByUser();

    List<MemberCalendar> memberCalendars = new ArrayList<>();
    for (TripMember member : members) {
      UUID memberUserId = member.getUser().getId();
      List<CalendarDay> days =
          resolvedByUser.getOrDefault(memberUserId, List.of()).stream()
              .map(
                  d -> new CalendarDay(
                      d.date(),
                      d.morningStatus(),
                      d.afternoonStatus(),
                      d.eveningStatus(),
                      d.uncertain()))
              .toList();
      memberCalendars.add(toMemberCalendar(member, displayNames, days));
    }
    return memberCalendars;
  }

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
      memberCalendars.add(toMemberCalendar(member, displayNames, days));
    }
    return memberCalendars;
  }

  private static MemberCalendar toMemberCalendar(
      TripMember member,
      Map<UUID, String> displayNames,
      List<CalendarDay> days) {
    UUID memberUserId = member.getUser().getId();
    return new MemberCalendar(
        memberUserId, displayNames.get(memberUserId), member.getRole(), member.getStatus(), days);
  }
}
