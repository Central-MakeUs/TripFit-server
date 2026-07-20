package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripHomeCardResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.repository.projection.TripMemberCountProjection;
import com.tripfit.tripfit.trip.repository.projection.TripMemberPreviewProjection;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class TripServiceSupport {

  static final int NAME_MAX_LENGTH = 15;

  static final int MAX_INVITE_CODE_ATTEMPTS = 20;

  static final int MEMBERS_PREVIEW_LIMIT = 4;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserRepository userRepository;

  TripServiceSupport(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserRepository userRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userRepository = userRepository;
  }

  TripHomeCardResponse toHomeCard(
      Trip trip,
      TripMember membership,
      int memberCount,
      int respondedCount,
      List<MemberPreviewResponse> previews) {
    int overflow = Math.max(0, memberCount - MEMBERS_PREVIEW_LIMIT);
    return new TripHomeCardResponse(
        trip.getId(),
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        effectiveStatus(trip),
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        respondedCount,
        memberCount,
        previews,
        overflow);
  }

  TripDetailResponse toDetail(Trip trip, TripMember membership) {
    UUID tripId = trip.getId();
    long memberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(tripId);
    int respondedCount =
        (int) tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            tripId,
            TripMemberStatus.RESPONDED);

    return new TripDetailResponse(
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
        trip.getLastActivityAt(),
        membership.isPinned(),
        membership.getRole(),
        membership.getStatus(),
        respondedCount,
        (int) memberCount);
  }

  Map<UUID, TripMemberCountProjection> loadMemberCountsByTripIds(List<UUID> tripIds) {
    return tripMemberRepository.countMembersByTripIds(tripIds).stream()
        .collect(Collectors.toMap(TripMemberCountProjection::getTripId, c -> c));
  }

  Map<UUID, List<MemberPreviewResponse>> loadMemberPreviewsByTripIds(List<UUID> tripIds) {
    List<TripMemberPreviewProjection> rows =
        tripMemberRepository.findMemberPreviewsByTripIds(tripIds);
    Map<UUID, List<MemberPreviewResponse>> byTrip = new HashMap<>();
    for (TripMemberPreviewProjection row : rows) {
      byTrip
          .computeIfAbsent(row.getTripId(), ignored -> new ArrayList<>())
          .add(
              new MemberPreviewResponse(
                  row.getUserId(),
                  row.getProfileImageUrl(),
                  TripMemberRole.valueOf(row.getRole())));
    }
    return byTrip;
  }

  Trip requireActiveTrip(UUID tripId) {
    return tripRepository
        .findByIdAndDeletedAtIsNull(tripId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
  }

  TripMember requireActiveMember(UUID tripId, UUID userId) {
    return tripMemberRepository
        .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
        .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
  }

  void requireOwner(Trip trip, UUID userId) {
    if (!trip.getOwner().getId().equals(userId)) {
      throw new TripFitException(TripErrorCode.TRIP_FORBIDDEN);
    }
  }

  void requireOngoingForMutation(Trip trip) {
    if (effectiveStatus(trip) != TripStatus.ONGOING) {
      throw new TripFitException(TripErrorCode.TRIP_NOT_ONGOING);
    }
  }

  // effectiveStatus: ONGOING + end_range 경과 → TERMINATED (배치 전 lazy · #27 이후 DB TERMINATED와 동일 UX)
  TripStatus effectiveStatus(Trip trip) {
    if (trip.getStatus() == TripStatus.ONGOING
        && trip.getEndRange().isBefore(LocalDate.now())) {
      return TripStatus.TERMINATED;
    }
    return trip.getStatus();
  }

  void validateTripMeta(
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

  String generateUniqueInviteCode() {
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

  User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
