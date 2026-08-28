package com.tripfit.tripfit.trip.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.trip.membership.service.TripJoinService;
import com.tripfit.tripfit.trip.membership.service.TripMemberQueryService;
import com.tripfit.tripfit.trip.recommendation.service.TripRecommendationService;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.trip.event.TripInfoChangedEvent;
import com.tripfit.tripfit.trip.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

class TripCommandService {
  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  private final TripJoinService tripJoinService;

  private final TripRecommendationService tripRecommendationService;

  private final TripMemberQueryService tripMemberQueryService;

  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public TripEntryResponse createTrip(UUID userId, CreateTripRequest request) {
    User owner = support.findUser(userId);

    support.requireProfileNameComplete(owner);
    support.validateTripMeta(
        request.name(),
        request.startRange(),
        request.endRange(),
        request.durationNights(),
        request.durationDays(),
        request.memberCount());
    Integer durationDays =
        TripServiceSupport.resolveDurationDays(request.durationNights(), request.durationDays());
    Trip trip =
        new Trip(
            owner,
            request.name().trim(),
            request.startRange(),
            request.endRange(),
            request.durationNights(),
            durationDays,
            request.memberCount(),
            support.generateUniqueInviteCode(),
            TripStatus.ONGOING);
    trip.applyDestination(TripServiceSupport.normalizeDestination(request.destination()));
    tripRepository.save(trip);

    TripMember ownerMember =
        new TripMember(
            trip,
            owner,
            TripMemberRole.OWNER,
            TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    tripMemberRepository.save(ownerMember);

    return support.toEntry(trip, ownerMember);
  }

  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripDetailResponse activateMembership(UUID tripId, UUID userId) {
    Trip trip = support.requireActiveTrip(tripId);
    TripMember membership = support.requireMembership(tripId, userId);
    if (membership.getStatus() != TripMemberStatus.ACTIVE) {
      requirePreScheduleCompleted(membership);
      membership.activate();
      publishEntryEvents(trip, membership);
    }
    return support.toDetail(trip, membership);
  }

  private void requirePreScheduleCompleted(TripMember membership) {
    if (!membership.getUser().hasCompletedPreSchedule()) {
      throw new TripFitException(UserErrorCode.PRE_SCHEDULE_REQUIRED);
    }
  }

  private void publishEntryEvents(Trip trip, TripMember membership) {
    if (membership.getRole() == TripMemberRole.MEMBER) {
      applicationEventPublisher.publishEvent(
          new TripJoinCompletedEvent(trip.getId(), membership.getUser().getId()));
    }
    long activeMemberCount =
        tripMemberRepository.countByTripIdAndActivatedAtIsNotNullAndDeletedAtIsNull(trip.getId());
    if (activeMemberCount >= trip.getMemberCount()) {
      applicationEventPublisher.publishEvent(new AllMembersSubmittedEvent(trip.getId()));
    }
  }

  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    Trip trip = support.requireOwnedOngoingTrip(tripId, userId);
    support.validateTripMeta(
        request.name(),
        trip.getStartRange(),
        trip.getEndRange(),
        request.durationNights(),
        request.durationDays(),
        request.memberCount());
    Integer durationDays =
        TripServiceSupport.resolveDurationDays(request.durationNights(), request.durationDays());
    boolean recommendationInputsChanged =
        !Objects.equals(trip.getDurationDays(), durationDays);
    String normalizedDestination = TripServiceSupport.normalizeDestination(request.destination());

    boolean valuesChanged =
        !Objects.equals(trip.getName(), request.name().trim())
            || !Objects.equals(trip.getDurationNights(), request.durationNights())
            || recommendationInputsChanged
            || !Objects.equals(trip.getMemberCount(), request.memberCount())
            || !Objects.equals(trip.getDestination(), normalizedDestination);
    trip.applyPatch(
        request.name().trim(),
        normalizedDestination,
        request.durationNights(),
        durationDays,
        request.memberCount());
    if (recommendationInputsChanged) {
      tripRecommendationService.deleteRecommendationsForTrip(tripId);
    }
    if (valuesChanged) {
      applicationEventPublisher.publishEvent(new TripInfoChangedEvent(tripId));
    }
    TripMember membership = support.requireMembership(tripId, userId);
    return support.toDetail(trip, membership);
  }

  @Transactional
  public void deleteTrip(UUID tripId, UUID userId) {
    Trip trip = support.requireOwnedTrip(tripId, userId);
    trip.markDeleted();
    for (TripMember member : tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)) {
      member.markDeleted();
    }
  }

  @Transactional
  public TripEntryResponse joinTrip(UUID userId, JoinTripRequest request) {

    Trip trip = findLockedTripByInviteCode(request);
    User user = support.findUser(userId);

    support.requireProfileNameComplete(user);
    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      return support.toEntry(trip, existing.get());
    }
    switch (support.effectiveStatus(trip)) {
      case CONFIRMED -> throw new TripFitException(TripErrorCode.TRIP_ALREADY_CONFIRMED);
      case EXPIRED -> throw new TripFitException(TripErrorCode.TRIP_EXPIRED);

      case ONGOING -> {
      }
    }
    return tripJoinService.joinAsNewMember(trip, user);
  }

  private Trip findLockedTripByInviteCode(JoinTripRequest request) {
    String inviteCode = request.inviteCode().trim().toUpperCase();
    return tripRepository
        .findByInviteCodeForUpdate(inviteCode)
        .orElseThrow(() -> new TripFitException(TripErrorCode.INVITE_CODE_NOT_FOUND));
  }

  @Transactional
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    TripMember membership = support.requireMembership(tripId, userId);

    membership.applyPin(Boolean.TRUE.equals(request.pinned()));
    return support.toDetail(membership.getTrip(), membership);
  }

  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripMembersResponse removeMember(UUID tripId, UUID ownerId, UUID targetUserId) {
    support.requireOwnedOngoingTrip(tripId, ownerId);
    TripMember target =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, targetUserId)
            .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_MEMBER_NOT_FOUND));
    if (target.getRole() == TripMemberRole.OWNER) {
      throw new TripFitException(TripErrorCode.CANNOT_REMOVE_OWNER);
    }
    target.markDeleted();
    return tripMemberQueryService.listMembers(tripId, ownerId);
  }

  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void leaveTrip(UUID tripId, UUID callerId) {
    support.requireActiveTrip(tripId);
    TripMember membership = support.requireMembership(tripId, callerId);
    if (membership.getRole() == TripMemberRole.OWNER) {
      throw new TripFitException(TripErrorCode.TRIP_OWNER_CANNOT_LEAVE);
    }
    membership.markDeleted();
  }
}
