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

  // 새로운 여행방을 생성합니다.
  // 1. 방장의 기본 프로필 검증 및 입력값 유효성 검사 수행
  // 2. 고유 초대 코드 발급 및 여행방 엔티티 생성 후 방장 멤버십 추가
  @Transactional
  public TripEntryResponse createTrip(UUID userId, CreateTripRequest request) {
    User owner = support.findUser(userId);

    // 1. 방장의 필수 프로필 정보(이름 등)가 입력되었는지 확인하고,
    // 여행 기본 정보(기간, 인원수 등)의 정책적 제약조건을 검증합니다.
    support.requireProfileNameComplete(owner);
    support.validateTripMeta(
        request.name(),
        request.startRange(),
        request.endRange(),
        request.durationNights(),
        request.durationDays(),
        request.memberCount());
    // 2. 입력된 정보로 Trip 엔티티를 초기화하고 초대 코드를 발급하여 저장합니다.
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

    // 3. 방장(Owner)을 해당 여행방의 첫 멤버로 등록합니다.
    // 방장 역시 최초에는 SCHEDULE_PENDING 상태(사전 일정 미입력 상태)로 시작합니다.
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

  // 사용자가 여행방에서 사전 일정 입력을 완료한 후 ACTIVE 상태로 멤버십을 활성화합니다.
  // 활성화 시 전체 멤버 제출 여부 등을 판단해 이벤트를 발행합니다.
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

  // 진행 중(ONGOING)인 여행방의 기본 정보를 수정합니다. (방장 전용)
  // 일정이나 멤버 수가 변경될 경우 기존 추천 스케줄을 초기화하고 이벤트를 발생시킵니다.
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    Trip trip = support.requireOwnedOngoingTrip(tripId, userId);

    // 1. 요청된 패치 데이터의 유효성을 검증하고, 기간(박/일)을 계산합니다.
    support.validateTripMeta(
        request.name(),
        trip.getStartRange(),
        trip.getEndRange(),
        request.durationNights(),
        request.durationDays(),
        request.memberCount());
    Integer durationDays =
        TripServiceSupport.resolveDurationDays(request.durationNights(), request.durationDays());

    // 2. 여행 일수가 변경되었는지 확인합니다. 일수가 변경되면 기존의 AI 추천 스케줄이 무효화됩니다.
    boolean recommendationInputsChanged =
        !Objects.equals(trip.getDurationDays(), durationDays);
    String normalizedDestination = TripServiceSupport.normalizeDestination(request.destination());

    // 3. 실제 값의 변경 여부를 체크하여 이벤트를 발행할지 결정한 뒤 데이터를 갱신합니다.
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

    // 4. 여행 일수 변경 시 기존 추천 결과를 모두 삭제합니다.
    if (recommendationInputsChanged) {
      tripRecommendationService.deleteRecommendationsForTrip(tripId);
    }

    // 5. 유의미한 정보 변경이 발생했다면 푸시 알림 등을 위한 이벤트를 발행합니다.
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

  // 초대 코드를 이용해 여행방에 참여합니다.
  // 잠금 조회(Pessimistic Lock)를 통해 동시 인원 초과를 방지합니다.
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
