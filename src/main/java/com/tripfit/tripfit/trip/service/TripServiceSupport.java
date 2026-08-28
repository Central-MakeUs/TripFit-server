package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.membership.service.InviteCodeGenerator;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.dto.TripHomeCardResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.membership.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.membership.repository.projection.TripMemberPreviewProjection;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.service.UserDirectoryService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TripServiceSupport {

  static final int NAME_MAX_LENGTH = 15;

  static final int MEMBER_COUNT_MIN = 1;

  static final int MEMBER_COUNT_MAX = 10;

  static final int MAX_INVITE_CODE_ATTEMPTS = 20;

  static final int MEMBERS_PREVIEW_LIMIT = 4;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserDirectoryService userDirectoryService;

  public TripServiceSupport(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserDirectoryService userDirectoryService) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userDirectoryService = userDirectoryService;
  }

  public TripHomeCardResponse toHomeCard(
      Trip trip,
      TripMember membership,
      int joinedMemberCount,
      int activeMemberCount,
      List<MemberPreviewResponse> previews) {
    return new TripHomeCardResponse(
        trip.getId(),
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getDurationNights(),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        activeMemberCount,
        memberFillRate(activeMemberCount, trip.getMemberCount()),
        previews,
        previewOverflow(joinedMemberCount));
  }

  public TripEntryResponse toEntry(Trip trip, TripMember membership) {
    return new TripEntryResponse(trip.getId(), effectiveStatus(trip), membership.getStatus());
  }

  public TripDetailResponse toDetail(Trip trip, TripMember membership) {
    UUID tripId = trip.getId();
    TripMemberCountProjection counts = loadMemberCountsByTripIds(List.of(tripId)).get(tripId);
    int joinedMemberCount = counts == null ? 0 : (int) counts.getJoinedMemberCount();
    int activeMemberCount = counts == null ? 0 : (int) counts.getActiveCount();
    List<MemberPreviewResponse> previews =
        loadMemberPreviewsByTripIds(List.of(tripId)).getOrDefault(tripId, List.of());

    return new TripDetailResponse(
        tripId,
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getDurationNights(),
        trip.getMemberCount(),
        effectiveStatus(trip),
        trip.getInviteCode(),
        trip.getConfirmedStartDate(),
        trip.getConfirmedEndDate(),
        trip.getConfirmedAttendCount(),
        trip.getConfirmedVacationMemberCount(),
        trip.getConfirmedUncertainCount(),
        trip.getLastRecommendationMode(),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        activeMemberCount,
        memberFillRate(activeMemberCount, trip.getMemberCount()),
        previews,
        previewOverflow(joinedMemberCount));
  }

  public static double memberFillRate(int activeMemberCount, Integer memberCount) {
    if (memberCount == null || memberCount <= 0) {
      return 0.0;
    }
    return (double) activeMemberCount / memberCount;
  }

  private static int previewOverflow(int joinedMemberCount) {
    return Math.max(0, joinedMemberCount - MEMBERS_PREVIEW_LIMIT);
  }

  public Map<UUID, TripMemberCountProjection> loadMemberCountsByTripIds(List<UUID> tripIds) {
    return tripMemberRepository.countMembersByTripIds(tripIds).stream()
        .collect(Collectors.toMap(TripMemberCountProjection::getTripId, c -> c));
  }

  public Map<UUID, List<MemberPreviewResponse>> loadMemberPreviewsByTripIds(List<UUID> tripIds) {
    List<TripMemberPreviewProjection> rows =
        tripMemberRepository.findMemberPreviewsByTripIds(tripIds);

    List<UUID> previewUserIds = rows.stream().map(TripMemberPreviewProjection::getUserId).toList();
    Map<UUID, User> usersById =
        userDirectoryService.findAllById(previewUserIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));

    Map<UUID, List<TripMemberPreviewProjection>> rowsByTrip = new LinkedHashMap<>();
    for (TripMemberPreviewProjection row : rows) {
      rowsByTrip.computeIfAbsent(row.getTripId(), ignored -> new ArrayList<>()).add(row);
    }

    Map<UUID, List<MemberPreviewResponse>> byTrip = new HashMap<>();
    for (Map.Entry<UUID, List<TripMemberPreviewProjection>> entry : rowsByTrip.entrySet()) {
      List<TripMemberPreviewProjection> tripRows = entry.getValue();

      List<User> usersInOrder =
          tripRows.stream().map(row -> usersById.get(row.getUserId())).toList();
      Map<UUID, String> displayNames =
          TripDisplayNameHelper.assignPreviewDisplayNames(usersInOrder);
      List<MemberPreviewResponse> previews =
          tripRows.stream()
              .map(
                  row -> new MemberPreviewResponse(
                      row.getUserId(),
                      displayNames.get(row.getUserId()),
                      row.getProfileImageUrl(),
                      TripMemberRole.valueOf(row.getRole())))
              .toList();
      byTrip.put(entry.getKey(), previews);
    }
    return byTrip;
  }

  public Trip requireActiveTrip(UUID tripId) {
    return tripRepository
        .findByIdAndDeletedAtIsNull(tripId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
  }

  public List<TripMember> listActiveMembersSortedByJoinedAt(UUID tripId) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId).stream()
        .sorted(Comparator.comparing(TripMember::getJoinedAt))
        .toList();
  }

  public TripMember requireMembership(UUID tripId, UUID userId) {
    return tripMemberRepository
        .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
  }

  public void requireActive(TripMember membership) {
    if (membership.getStatus() != TripMemberStatus.ACTIVE) {
      throw new TripFitException(UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED);
    }
  }

  public void requireOwner(Trip trip, UUID userId) {
    if (!trip.getOwner().getId().equals(userId)) {
      throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
    }
  }

  public void requireOngoingForMutation(Trip trip) {
    if (effectiveStatus(trip) != TripStatus.ONGOING) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_ONGOING);
    }
  }

  public Trip requireOwnedTrip(UUID tripId, UUID userId) {
    Trip trip = requireActiveTrip(tripId);
    requireOwner(trip, userId);
    return trip;
  }

  public Trip requireOwnedOngoingTrip(UUID tripId, UUID userId) {
    Trip trip = requireOwnedTrip(tripId, userId);
    requireOngoingForMutation(trip);
    return trip;
  }

  public TripStatus effectiveStatus(Trip trip) {
    if (trip.getStatus() == TripStatus.ONGOING
        && trip.getEndRange().isBefore(LocalDate.now())) {
      return TripStatus.EXPIRED;
    }
    return trip.getStatus();
  }

  public void validateTripMeta(
      String name,
      LocalDate startRange,
      LocalDate endRange,
      Integer durationNights,
      Integer durationDays,
      Integer memberCount) {
    if (name == null || name.isBlank() || name.trim().length() > NAME_MAX_LENGTH) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (startRange == null
        || endRange == null
        || endRange.isBefore(startRange)
        || memberCount == null
        || memberCount < MEMBER_COUNT_MIN
        || memberCount > MEMBER_COUNT_MAX) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    Integer resolvedDays = resolveDurationDays(durationNights, durationDays);
    if (resolvedDays != null) {
      long rangeDays = ChronoUnit.DAYS.between(startRange, endRange) + 1;
      if (resolvedDays > rangeDays) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
    }
  }

  static Integer resolveDurationDays(Integer durationNights, Integer durationDays) {
    if (durationNights == null && durationDays == null) {
      return null;
    }
    if (durationNights == null
        || durationDays == null
        || durationNights < 0
        || durationDays < durationNights + 1
        || durationDays > durationNights + 2) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    return durationDays;
  }

  public String generateUniqueInviteCode() {
    for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
      String code = InviteCodeGenerator.generate();
      if (!tripRepository.existsByInviteCode(code)) {
        return code;
      }
    }
    throw new TripFitException(CommonErrorCode.INTERNAL_ERROR);
  }

  static String normalizeDestination(String destination) {
    if (destination == null || destination.isBlank()) {
      return null;
    }
    return destination.trim();
  }

  public User findUser(UUID userId) {
    return userDirectoryService.requireUser(userId);
  }

  public void requireProfileNameComplete(User user) {
    userDirectoryService.requireProfileNameComplete(user);
  }
}
