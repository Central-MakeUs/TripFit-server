package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.membership.service.TripJoinHoldService;
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
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.membership.dto.TripJoinPreviewResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.trip.event.TripInfoChangedEvent;
import com.tripfit.tripfit.trip.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 여행방 생성·참여·멤버십 activate·메타 수정·삭제·Pin·내보내기 등 쓰기 유스케이스
class TripCommandService {
  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  private final TripJoinService tripJoinService;

  private final TripJoinHoldService tripJoinHoldService;

  private final TripRecommendationService tripRecommendationService;

  private final TripMemberQueryService tripMemberQueryService;

  private final UserDirectoryPort userDirectoryPort;

  private final ApplicationEventPublisher applicationEventPublisher;

  TripCommandService(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      TripServiceSupport support,
      TripJoinService tripJoinService,
      TripJoinHoldService tripJoinHoldService,
      TripRecommendationService tripRecommendationService,
      TripMemberQueryService tripMemberQueryService,
      UserDirectoryPort userDirectoryPort,
      ApplicationEventPublisher applicationEventPublisher) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.support = support;
    this.tripJoinService = tripJoinService;
    this.tripJoinHoldService = tripJoinHoldService;
    this.tripRecommendationService = tripRecommendationService;
    this.tripMemberQueryService = tripMemberQueryService;
    this.userDirectoryPort = userDirectoryPort;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  // 여행방 생성 — 방장은 SCHEDULE_PENDING(일정 확인 전). activate 전에는 ACTIVE가 아님
  @Transactional
  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
    User owner = support.findUser(userId);
    // 성·이름 미완료면 생성 불가
    userDirectoryPort.requireProfileNameComplete(owner);
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
    trip.setDestination(TripServiceSupport.normalizeDestination(request.destination()));
    tripRepository.save(trip);
    // create 직후는 SCHEDULE_PENDING — 일정 activate 후에 ACTIVE. 전부 free 처리는 activate/join에서.
    TripMember ownerMember =
        new TripMember(
            trip,
            owner,
            TripMemberRole.OWNER,
            TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    tripMemberRepository.save(ownerMember);
    // inviteCode는 DB에만 발급 — SCHEDULE_PENDING(입장 전) 생성 응답에는 안 실림. 공유는 activate 후 상세에서
    return new CreateTripResponse(
        trip.getId(), support.effectiveStatus(trip), ownerMember.getStatus());
  }

  // 방장 멤버십을 SCHEDULE_PENDING→ACTIVE로 activate — 이미 ACTIVE면 동일 상세 반환(idempotent)
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public TripDetailResponse activateMembership(UUID tripId, UUID userId) {
    Trip trip = support.requireActiveTrip(tripId);
    TripMember membership = support.requireActiveMember(tripId, userId);
    if (membership.getStatus() != TripMemberStatus.ACTIVE) {
      // 일정이 0건이면 전부 free로 표시한 뒤 ACTIVE로 전환 — canEnterRoom을 항상 충족시키므로 별도 재검증 불필요
      userDirectoryPort.markAllFreeIfNoSchedules(membership.getUser());
      membership.activate();
    }
    return support.toDetail(trip, membership);
  }

  // 방장만 메타 수정 — 희망 박/일이 바뀌면 기존 추천 후보를 삭제한다
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
    // NOTI-003(D12) — no-op patch는 미발송하므로 실제 값 변경 여부를 저장 전에 비교
    boolean valuesChanged =
        !Objects.equals(trip.getName(), request.name().trim())
            || !Objects.equals(trip.getDurationNights(), request.durationNights())
            || recommendationInputsChanged
            || !Objects.equals(trip.getMemberCount(), request.memberCount())
            || !Objects.equals(trip.getDestination(), normalizedDestination);
    trip.setName(request.name().trim());
    trip.setDurationNights(request.durationNights());
    trip.setDurationDays(durationDays);
    trip.setMemberCount(request.memberCount());
    trip.setDestination(normalizedDestination);
    if (recommendationInputsChanged) {
      tripRecommendationService.deleteRecommendationsForTrip(tripId);
    }
    if (valuesChanged) {
      applicationEventPublisher.publishEvent(new TripInfoChangedEvent(tripId));
    }
    TripMember membership = support.requireActiveMember(tripId, userId);
    return support.toDetail(trip, membership);
  }

  // 방장이 여행방을 soft delete — 멤버 row도 연쇄 soft delete
  @Transactional
  public void deleteTrip(UUID tripId, UUID userId) {
    Trip trip = support.requireOwnedTrip(tripId, userId);
    LocalDateTime now = LocalDateTime.now();
    trip.setDeletedAt(now);
    for (TripMember member : tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)) {
      member.setDeletedAt(now);
    }
  }

  // 초대코드로 여행방을 미리보기하며 10분 hold를 생성한다 — 참여 플로우 진입 시점에 정원 자리를 선점(#35).
  // 이미 멤버면 hold 없이 동일 정보 반환. 정원(확정 멤버 수 + 활성 hold 수) 초과면 hold 없이 409
  @Transactional(readOnly = true)
  public TripJoinPreviewResponse previewAndHold(UUID userId, JoinTripRequest request) {
    User user = support.findUser(userId);
    userDirectoryPort.requireProfileNameComplete(user);
    Trip trip = findTripByInviteCode(request);
    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      support.requireActive(existing.get());
      return toPreview(trip);
    }
    TripStatus status = support.effectiveStatus(trip);
    switch (status) {
      case CONFIRMED -> throw new TripFitException(TripErrorCode.TRIP_ALREADY_CONFIRMED);
      case EXPIRED -> throw new TripFitException(TripErrorCode.TRIP_EXPIRED);
      case ONGOING -> {
        long joinedMemberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId());
        boolean held =
            tripJoinHoldService.tryHold(
                trip.getId(),
                userId,
                joinedMemberCount,
                trip.getMemberCount());
        if (!held) {
          throw new TripFitException(TripErrorCode.TRIP_MEMBER_FULL);
        }
      }
    }
    return toPreview(trip);
  }

  // 참여 플로우 첫 화면 이탈 시 hold를 즉시 반환한다 — 존재하지 않거나 이미 만료·소비된 hold도 안전한 no-op
  public void releaseJoinHold(UUID tripId, UUID userId) {
    tripJoinHoldService.release(tripId, userId);
  }

  // 초대코드로 참여 — 신규 멤버는 바로 ACTIVE. SCHEDULE_PENDING(activate 전 방장)는 activate로 유도.
  // 유효한 hold가 있으면 정원을 재확인하지 않고 확정(hold 발급 시 이미 원자적으로 보장됨) — 없으면(만료·미호출)
  // 확정 멤버 수 + 다른 사용자의 활성 hold 수를 함께 봐서 기존 D8 정원 체크로 폴백
  @Transactional
  public TripDetailResponse joinTrip(UUID userId, JoinTripRequest request) {
    User user = support.findUser(userId);
    // 성·이름 미완료면 참여 불가
    userDirectoryPort.requireProfileNameComplete(user);
    Trip trip = findTripByInviteCode(request);
    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      TripMember membership = existing.get();
      // SCHEDULE_PENDING면 join으로 상세를 우회하지 못함 — activate 필요
      support.requireActive(membership);
      return support.toDetail(trip, membership);
    }
    TripStatus status = support.effectiveStatus(trip);
    long joinedMemberCount = 0;
    switch (status) {
      case CONFIRMED -> throw new TripFitException(TripErrorCode.TRIP_ALREADY_CONFIRMED);
      case EXPIRED -> throw new TripFitException(TripErrorCode.TRIP_EXPIRED);
      case ONGOING -> {
        joinedMemberCount = tripMemberRepository.countByTripIdAndDeletedAtIsNull(trip.getId());
        boolean hasHold = tripJoinHoldService.hasActiveHold(trip.getId(), userId);
        if (!hasHold) {
          long othersActiveHolds = tripJoinHoldService.countActiveHolds(trip.getId());
          if (joinedMemberCount + othersActiveHolds >= trip.getMemberCount()) {
            throw new TripFitException(TripErrorCode.TRIP_MEMBER_FULL);
          }
        }
      }
    }
    TripDetailResponse response = tripJoinService.joinAsNewMember(trip, user);
    tripJoinHoldService.release(trip.getId(), userId);
    applicationEventPublisher.publishEvent(new TripJoinCompletedEvent(trip.getId(), userId));
    // D11 — 멤버는 join 즉시 ACTIVE라 "정원 도달"이 곧 전원 제출 완료. 방금 저장한 신규 멤버만큼 +1해 재조회 없이 판정
    if (joinedMemberCount + 1 >= trip.getMemberCount()) {
      applicationEventPublisher.publishEvent(new AllMembersSubmittedEvent(trip.getId()));
    }
    return response;
  }

  private Trip findTripByInviteCode(JoinTripRequest request) {
    String inviteCode = request.inviteCode().trim().toUpperCase();
    return tripRepository
        .findByInviteCodeAndDeletedAtIsNull(inviteCode)
        .orElseThrow(() -> new TripFitException(TripErrorCode.INVITE_CODE_NOT_FOUND));
  }

  private TripJoinPreviewResponse toPreview(Trip trip) {
    var counts =
        support.loadMemberCountsByTripIds(java.util.List.of(trip.getId())).get(trip.getId());
    int activeMemberCount = counts == null ? 0 : (int) counts.getActiveCount();
    return new TripJoinPreviewResponse(
        trip.getId(),
        trip.getName(),
        trip.getDestination(),
        trip.getStartRange(),
        trip.getEndRange(),
        trip.getDurationDays(),
        trip.getDurationNights(),
        trip.getMemberCount(),
        activeMemberCount,
        support.effectiveStatus(trip));
  }

  // 멤버 Pin on/off — 만료 Pin 자동 해제는 일 배치(TripHomeMaintenanceService)
  @Transactional
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    TripMember membership = support.requireActiveMember(tripId, userId);
    // 조회 API에서 Pin을 부수적으로 쓰지 않음 — 해제는 배치만
    membership.applyPin(Boolean.TRUE.equals(request.pinned()));
    return support.toDetail(membership.getTrip(), membership);
  }

  // 방장이 MEMBER를 soft delete — 추천 후보는 건드리지 않고, 대상 일정 row는 유지
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
    target.setDeletedAt(LocalDateTime.now());
    return tripMemberQueryService.listMembers(tripId, ownerId);
  }

  // 멤버가 스스로 여행방에서 나간다 — 방 상태 무관(내보내기와 달리 ONGOING 게이트 없음), 방장은 불가
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void leaveTrip(UUID tripId, UUID callerId) {
    support.requireActiveTrip(tripId);
    TripMember membership = support.requireActiveMember(tripId, callerId);
    if (membership.getRole() == TripMemberRole.OWNER) {
      throw new TripFitException(TripErrorCode.TRIP_OWNER_CANNOT_LEAVE);
    }
    membership.setDeletedAt(LocalDateTime.now());
  }
}
