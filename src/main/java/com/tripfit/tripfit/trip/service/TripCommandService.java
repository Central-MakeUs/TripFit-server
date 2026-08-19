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
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
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
// 여행방 생성·참여·멤버십 activate·메타 수정·삭제·Pin·내보내기 등 쓰기 유스케이스
class TripCommandService {
  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  private final TripJoinService tripJoinService;

  private final TripRecommendationService tripRecommendationService;

  private final TripMemberQueryService tripMemberQueryService;

  private final UserDirectoryPort userDirectoryPort;

  private final ApplicationEventPublisher applicationEventPublisher;

  // 여행방 생성 — 방장은 SCHEDULE_PENDING(일정 확인 전). activate 전에는 ACTIVE가 아님
  @Transactional
  public TripEntryResponse createTrip(UUID userId, CreateTripRequest request) {
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
    trip.applyDestination(TripServiceSupport.normalizeDestination(request.destination()));
    tripRepository.save(trip);
    // create 직후는 SCHEDULE_PENDING — 일정 activate 후에 ACTIVE.
    TripMember ownerMember =
        new TripMember(
            trip,
            owner,
            TripMemberRole.OWNER,
            TripMemberStatus.SCHEDULE_PENDING,
            LocalDateTime.now());
    tripMemberRepository.save(ownerMember);
    // inviteCode는 DB에만 발급 — SCHEDULE_PENDING(입장 전) 생성 응답에는 안 실림. 공유는 activate 후 상세에서
    return support.toEntry(trip, ownerMember);
  }

  // 일정 확인을 끝내 멤버십을 SCHEDULE_PENDING→ACTIVE로 바꾼다 — 방장·참여자 모두 이 경로로 방에 들어온다.
  // 이미 ACTIVE면 상태·알림 변화 없이 동일 상세를 반환한다(idempotent)
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

  // 사전 일정 입력을 한 번도 끝내지 않은 사용자는 ACTIVE가 될 수 없다. 프론트가 마지막 버튼을 막고 있지만,
  // 추천이 "일정 0건인 ACTIVE 멤버 = 모든 날 가능"으로 계산하는 이상 그 전제를 서버가 직접 지켜야 한다 —
  // 플로우를 건너뛰고 activate만 호출하면 아무 답도 안 한 사람이 전부 가능한 사람으로 집계된다.
  // 이미 ACTIVE인 멱등 재호출은 상태 전환이 없어 이 검사를 타지 않는다
  private void requirePreScheduleCompleted(TripMember membership) {
    if (!membership.getUser().hasCompletedPreSchedule()) {
      throw new TripFitException(UserErrorCode.PRE_SCHEDULE_REQUIRED);
    }
  }

  // 방 입장이 실제로 완료된 순간에만 알린다 — join은 초대 링크를 연 시점일 뿐이라 알림 근거가 되지 못한다.
  // 1. 참여 완료: 방장의 create-activate는 자기 방이라 알릴 대상이 아니므로 참여자만 발행
  // 2. 전원 제출: "자리가 찼다"가 아니라 "전원이 일정 확인을 마쳤다"로 판정 — 방금 activate한 본인도 포함되도록
  // flush 이후 값을 세는 count 쿼리를 쓴다
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

  // 방장이 여행방을 soft delete — 멤버 row도 연쇄 soft delete
  @Transactional
  public void deleteTrip(UUID tripId, UUID userId) {
    Trip trip = support.requireOwnedTrip(tripId, userId);
    trip.markDeleted();
    for (TripMember member : tripMemberRepository.findByTripIdAndDeletedAtIsNull(tripId)) {
      member.markDeleted();
    }
  }

  // 초대 링크로 참여 — 신규 멤버는 SCHEDULE_PENDING으로 생기고, 일정 확인을 마친 뒤 activate로 ACTIVE가 된다.
  // 이미 멤버면 새 row·알림 없이 현재 상태만 돌려준다(멱등) — 링크를 다시 열어도 같은 응답이라
  // 클라이언트가 에러 코드가 아니라 myMemberStatus 하나로 라우팅한다
  @Transactional
  public TripEntryResponse joinTrip(UUID userId, JoinTripRequest request) {
    // 1. 방 조회가 곧 자리 확보 락이다 — 이 트랜잭션의 첫 조회여야 한다. 다른 조회를 먼저 하면 그 시점으로
    // 읽기 스냅샷이 고정돼, 그 뒤 커밋된 다른 참여자의 멤버 row가 아래 정원 카운트에서 빠진다
    Trip trip = findLockedTripByInviteCode(request);
    User user = support.findUser(userId);
    // 성·이름 미완료면 참여 불가
    userDirectoryPort.requireProfileNameComplete(user);
    var existing =
        tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(trip.getId(), userId);
    if (existing.isPresent()) {
      return support.toEntry(trip, existing.get());
    }
    switch (support.effectiveStatus(trip)) {
      case CONFIRMED -> throw new TripFitException(TripErrorCode.TRIP_ALREADY_CONFIRMED);
      case EXPIRED -> throw new TripFitException(TripErrorCode.TRIP_EXPIRED);
      // 정원은 락을 잡은 뒤 세야 정확하므로 joinAsNewMember 안에서 확인한다
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

  // 멤버 Pin on/off — 만료 Pin 자동 해제는 일 배치(TripHomeMaintenanceService)
  @Transactional
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    TripMember membership = support.requireMembership(tripId, userId);
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
    target.markDeleted();
    return tripMemberQueryService.listMembers(tripId, ownerId);
  }

  // 멤버가 스스로 여행방에서 나간다 — 방 상태 무관(내보내기와 달리 ONGOING 게이트 없음), 방장은 불가.
  // 멤버십 상태(ACTIVE) 게이트는 Controller의 @TripMemberOnly가 담당한다 — 여기서 막으면 회원 탈퇴 cascade가
  // SCHEDULE_PENDING 멤버십을 정리하지 못한다
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
