package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import com.tripfit.tripfit.user.service.UserProfileService;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TripCommandService {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserProfileService userProfileService;

  private final ScheduleService scheduleService;

  private final RecommendationRepository recommendationRepository;

  private final TripServiceSupport support;

  private final TripQueryService tripQueryService;

  TripCommandService(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserProfileService userProfileService,
      ScheduleService scheduleService,
      RecommendationRepository recommendationRepository,
      TripServiceSupport support,
      TripQueryService tripQueryService) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userProfileService = userProfileService;
    this.scheduleService = scheduleService;
    this.recommendationRepository = recommendationRepository;
    this.support = support;
    this.tripQueryService = tripQueryService;
  }

  @Transactional
  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
    User owner = support.findUser(userId);
    // BR-USER-001: 여행방 생성 전 성·이름 필수
    userProfileService.requireProfileNameComplete(owner);
    support.validateTripMeta(
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
            support.generateUniqueInviteCode(),
            TripStatus.ONGOING);
    trip.setDestination(TripServiceSupport.normalizeDestination(request.destination()));
    tripRepository.save(trip);

    TripMember ownerMember =
        new TripMember(
            trip,
            owner,
            TripMemberRole.OWNER,
            TripMemberStatus.JOINED,
            LocalDateTime.now());
    tripMemberRepository.save(ownerMember);

    return new CreateTripResponse(
        trip.getId(), trip.getInviteCode(), support.effectiveStatus(trip));
  }

  @Transactional
  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    Trip trip = support.requireActiveTrip(tripId);
    support.requireOwner(trip, userId);
    support.requireOngoingForMutation(trip);

    support.validateTripMeta(
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
    trip.setDestination(TripServiceSupport.normalizeDestination(request.destination()));
    // #26: 갱신 이벤트 SSOT·AOP 확정 전 최소 hook
    trip.touchLastActivity();

    if (recommendationInputsChanged) {
      // BR-TRIP-010: recommendation hard DELETE — #13 TripRecommendationService와 통합 예정
      recommendationRepository.deleteByTripId(tripId);
    }

    TripMember membership =
        tripMemberRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(tripId, userId)
            .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));
    return tripQueryService.toDetail(trip, membership);
  }

  @Transactional
  public void deleteTrip(UUID tripId, UUID userId) {
    Trip trip = support.requireActiveTrip(tripId);
    support.requireOwner(trip, userId);

    LocalDateTime now = LocalDateTime.now();
    trip.setDeletedAt(now);
    // trip soft delete 시 멤버 row도 연쇄 soft delete
    for (TripMember member : tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)) {
      member.setDeletedAt(now);
    }
  }

  @Transactional
  public TripDetailResponse joinTrip(UUID userId, JoinTripRequest request) {
    User user = support.findUser(userId);
    String inviteCode = request.inviteCode().trim().toUpperCase();

    Trip trip =
        tripRepository
            .findByInviteCodeAndDeletedAtIsNull(inviteCode)
            .orElseThrow(() -> new TripFitException(TripErrorCode.INVITE_CODE_NOT_FOUND));

    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      return tripQueryService.toDetail(trip, existing.get());
    }

    TripStatus status = support.effectiveStatus(trip);
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
    trip.touchLastActivity();
    return tripQueryService.toDetail(trip, member);
  }

  @Transactional
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    TripMember membership = support.requireActiveMember(tripId, userId);
    // Pin 자동 해제는 #27 스케줄러 — 조회 API 부수 write 없음
    membership.applyPin(Boolean.TRUE.equals(request.pinned()));
    return tripQueryService.toDetail(membership.getTrip(), membership);
  }

  @Transactional
  public TripDetailResponse submitSchedule(UUID tripId, UUID userId) {
    Trip trip = support.requireActiveTrip(tripId);
    support.requireOngoingForMutation(trip);
    TripMember membership = support.requireActiveMember(tripId, userId);

    scheduleService.requireRegularScheduleRegistered(userId);
    membership.setStatus(TripMemberStatus.RESPONDED);
    trip.touchLastActivity();
    return tripQueryService.toDetail(trip, membership);
  }
}
