package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.CalendarDay;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse.MemberCalendar;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse.TripMemberItemResponse;
import com.tripfit.tripfit.trip.dto.TripSummaryResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import com.tripfit.tripfit.user.service.UserProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

  private static final int NAME_MAX_LENGTH = 15;

  private static final int MAX_INVITE_CODE_ATTEMPTS = 20;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserRepository userRepository;

  private final UserProfileService userProfileService;

  private final ScheduleService scheduleService;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final RecommendationRepository recommendationRepository;

  public TripService(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserRepository userRepository,
      UserProfileService userProfileService,
      ScheduleService scheduleService,
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      RecommendationRepository recommendationRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userRepository = userRepository;
    this.userProfileService = userProfileService;
    this.scheduleService = scheduleService;
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.recommendationRepository = recommendationRepository;
  }

  @Transactional
  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
    User owner = findUser(userId);
    userProfileService.requireProfileNameComplete(owner);
    validateTripMeta(
        request.name(),
        request.startRange(),
        request.endRange(),
        request.durationDays(),
        request.targetMemberCount());

    Trip trip =
        new Trip(
            owner,
            request.name().trim(),
            request.startRange(),
            request.endRange(),
            request.durationDays(),
            request.targetMemberCount(),
            generateUniqueInviteCode(),
            TripStatus.ONGOING);
    trip.setDestination(normalizeDestination(request.destination()));
    tripRepository.save(trip);

    TripMember ownerMember =
        new TripMember(
            trip,
            owner,
            TripMemberRole.OWNER,
            TripMemberStatus.JOINED,
            LocalDateTime.now());
    tripMemberRepository.save(ownerMember);

    return new CreateTripResponse(trip.getId(), trip.getInviteCode(), effectiveStatus(trip));
  }

  @Transactional(readOnly = true)
  public TripListResponse listMyTrips(UUID userId) {
    List<TripMember> memberships = tripMemberRepository.findActiveMembershipsByUserId(userId);
    List<TripSummaryResponse> trips =
        memberships.stream().map(m -> toSummary(m.getTrip(), m)).toList();
    return new TripListResponse(trips);
  }

  @Transactional(readOnly = true)
  public TripSummaryResponse getTrip(UUID tripId, UUID userId) {
    TripMember membership = requireActiveMember(tripId, userId);
    return toSummary(membership.getTrip(), membership);
  }

  @Transactional
  public TripSummaryResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    Trip trip = requireActiveTrip(tripId);
    requireOwner(trip, userId);
    requireOngoingForMutation(trip);

    validateTripMeta(
        request.name(),
        request.startRange(),
        request.endRange(),
        request.durationDays(),
        request.targetMemberCount());

    boolean recommendationInputsChanged =
        !Objects.equals(trip.getStartRange(), request.startRange())
            || !Objects.equals(trip.getEndRange(), request.endRange())
            || !Objects.equals(trip.getDurationDays(), request.durationDays());

    trip.setName(request.name().trim());
    trip.setStartRange(request.startRange());
    trip.setEndRange(request.endRange());
    trip.setDurationDays(request.durationDays());
    trip.setTargetMemberCount(request.targetMemberCount());
    trip.setDestination(normalizeDestination(request.destination()));

    if (recommendationInputsChanged) {
      // BR-TRIP-010: recommendation hard DELETE — #13 TripRecommendationService와 통합 예정
      recommendationRepository.deleteByTripId(tripId);
    }

    TripMember membership =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
            .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
    return toSummary(trip, membership);
  }

  @Transactional
  public void deleteTrip(UUID tripId, UUID userId) {
    Trip trip = requireActiveTrip(tripId);
    requireOwner(trip, userId);

    LocalDateTime now = LocalDateTime.now();
    trip.setDeletedAt(now);
    for (TripMember member : tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)) {
      member.setDeletedAt(now);
    }
  }

  @Transactional
  public TripSummaryResponse joinTrip(UUID userId, JoinTripRequest request) {
    User user = findUser(userId);
    String inviteCode = request.inviteCode().trim().toUpperCase();

    Trip trip =
        tripRepository
            .findByInviteCodeAndDeletedAtIsNull(inviteCode)
            .orElseThrow(() -> new TripFitException(TripErrorCode.INVITE_CODE_NOT_FOUND));

    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      return toSummary(trip, existing.get());
    }

    TripStatus status = effectiveStatus(trip);
    switch (status) {
      case CONFIRMED -> throw new TripFitException(TripErrorCode.TRIP_ALREADY_CONFIRMED);
      case CANCELED -> throw new TripFitException(TripErrorCode.TRIP_CANCELED);
      case TERMINATED -> throw new TripFitException(TripErrorCode.TRIP_TERMINATED);
      case ONGOING -> {
        long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId());
        if (memberCount >= trip.getTargetMemberCount()) {
          throw new TripFitException(TripErrorCode.TRIP_MEMBER_FULL);
        }
      }
    }

    TripMember member =
        new TripMember(
            trip,
            user,
            TripMemberRole.MEMBER,
            TripMemberStatus.JOINED,
            LocalDateTime.now());
    tripMemberRepository.save(member);
    return toSummary(trip, member);
  }

  @Transactional
  public TripSummaryResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    TripMember membership = requireActiveMember(tripId, userId);
    membership.setPinned(request.pinned());
    return toSummary(membership.getTrip(), membership);
  }

  @Transactional
  public TripSummaryResponse submitSchedule(UUID tripId, UUID userId) {
    Trip trip = requireActiveTrip(tripId);
    requireOngoingForMutation(trip);
    TripMember membership = requireActiveMember(tripId, userId);

    scheduleService.requireRegularScheduleRegistered(userId);
    membership.setStatus(TripMemberStatus.RESPONDED);
    return toSummary(trip, membership);
  }

  @Transactional(readOnly = true)
  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    requireActiveMember(tripId, userId);
    Trip trip = requireActiveTrip(tripId);

    List<TripMember> members =
        tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
            .sorted(Comparator.comparing(TripMember::getJoinedAt))
            .toList();

    List<User> usersInOrder = members.stream().map(TripMember::getUser).toList();
    Map<UUID, String> displayNames = TripDisplayNameHelper.assignDisplayNames(usersInOrder);

    int memberCount = members.size();
    int respondedCount =
        (int) members.stream()
            .filter(m -> m.getStatus() == TripMemberStatus.RESPONDED)
            .count();
    double responseRate = memberCount == 0 ? 0.0 : (double) respondedCount / memberCount;

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

    return new TripMembersResponse(memberCount, respondedCount, responseRate, items);
  }

  @Transactional(readOnly = true)
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    requireActiveMember(tripId, userId);
    Trip trip = requireActiveTrip(tripId);
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

  private TripSummaryResponse toSummary(Trip trip, TripMember membership) {
    UUID tripId = trip.getId();
    long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId);
    int respondedCount =
        (int) tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            tripId,
            TripMemberStatus.RESPONDED);

    return new TripSummaryResponse(
        tripId,
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getTargetMemberCount(),
        effectiveStatus(trip),
        trip.getInviteCode(),
        trip.getConfirmedStartDate(),
        trip.getConfirmedEndDate(),
        trip.getLastRecommendationMode(),
        membership.isPinned(),
        membership.getStatus(),
        respondedCount,
        (int) memberCount);
  }

  private Trip requireActiveTrip(UUID tripId) {
    return tripRepository
        .findByIdAndDeletedAtIsNull(tripId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
  }

  private TripMember requireActiveMember(UUID tripId, UUID userId) {
    return tripMemberRepository
        .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
  }

  private void requireOwner(Trip trip, UUID userId) {
    if (!trip.getOwner().getId().equals(userId)) {
      throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
    }
  }

  private void requireOngoingForMutation(Trip trip) {
    if (effectiveStatus(trip) != TripStatus.ONGOING) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_ONGOING);
    }
  }

  private TripStatus effectiveStatus(Trip trip) {
    if (trip.getStatus() == TripStatus.ONGOING
        && trip.getEndRange().isBefore(LocalDate.now())) {
      return TripStatus.TERMINATED;
    }
    return trip.getStatus();
  }

  private void validateTripMeta(
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationDays,
      Integer targetMemberCount) {
    if (name == null || name.isBlank() || name.trim().length() > NAME_MAX_LENGTH) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (startRange == null
        || endRange == null
        || endRange.isBefore(startRange)
        || durationDays == null
        || durationDays < 1
        || targetMemberCount == null
        || targetMemberCount < 1) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    long rangeDays = ChronoUnit.DAYS.between(startRange, endRange) + 1;
    if (durationDays > rangeDays) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private String generateUniqueInviteCode() {
    for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
      String code = InviteCodeGenerator.generate();
      if (!tripRepository.existsByInviteCode(code)) {
        return code;
      }
    }
    throw new TripFitException(CommonErrorCode.INTERNAL_ERROR);
  }

  private static String normalizeDestination(String destination) {
    if (destination == null || destination.isBlank()) {
      return null;
    }
    return destination.trim();
  }

  private User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
