package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripHomeCardResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.projection.TripMemberCountProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 여행방 목록·상세 조회 (쓰기 없음)
@Service
class TripQueryService {

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  TripQueryService(TripMemberRepository tripMemberRepository, TripServiceSupport support) {
    this.tripMemberRepository = tripMemberRepository;
    this.support = support;
  }

  // 내 여행방 홈 카드 목록을 scope(ongoing|all)·status·ownerOnly로 조회한다
  @Transactional(readOnly = true)
  public TripListResponse listMyTrips(UUID userId, TripListQuery query) {
    LocalDate today = LocalDate.now();
    String statusFilterName = query.statusFilter().map(Enum::name).orElse("ALL");
    List<TripMember> memberships =
        switch (query.scope()) {
          case ONGOING -> tripMemberRepository.findOngoingMembershipsByUserId(userId, today);
          case ALL -> tripMemberRepository.findAllMembershipsByUserId(
              userId,
              today,
              statusFilterName,
              query.ownerOnly());
        };

    if (memberships.isEmpty()) {
      return new TripListResponse(List.of());
    }

    List<UUID> tripIds = memberships.stream().map(m -> m.getTrip().getId()).distinct().toList();
    Map<UUID, TripMemberCountProjection> countsByTripId =
        support.loadMemberCountsByTripIds(tripIds);
    Map<UUID, List<MemberPreviewResponse>> previewsByTripId =
        support.loadMemberPreviewsByTripIds(tripIds);

    List<TripHomeCardResponse> trips =
        memberships.stream()
            .map(
                m -> {
                  UUID tripId = m.getTrip().getId();
                  TripMemberCountProjection counts = countsByTripId.get(tripId);
                  int joinedMemberCount =
                      counts == null ? 0 : (int) counts.getJoinedMemberCount();
                  int respondedCount = counts == null ? 0 : (int) counts.getRespondedCount();
                  return support.toHomeCard(
                      m.getTrip(),
                      m,
                      joinedMemberCount,
                      respondedCount,
                      previewsByTripId.getOrDefault(tripId, List.of()));
                })
            .toList();
    return new TripListResponse(trips);
  }

  // 여행방 상세 조회 — 활성 멤버십이 있어야 함
  @Transactional(readOnly = true)
  public TripDetailResponse getTrip(UUID tripId, UUID userId) {
    TripMember membership = support.requireActiveMember(tripId, userId);
    return support.toDetail(membership.getTrip(), membership);
  }

  // Trip·멤버십으로 상세 DTO 매핑 (command 경로에서도 재사용)
  TripDetailResponse toDetail(com.tripfit.tripfit.trip.domain.Trip trip, TripMember membership) {
    return support.toDetail(trip, membership);
  }

  // 회원 탈퇴 cascade — 특정 role로 활성 참여 중인 tripId 목록
  List<UUID> listActiveTripIdsByRole(UUID userId, TripMemberRole role) {
    return tripMemberRepository.findByUser_IdAndRoleAndDeletedAtIsNull(userId, role).stream()
        .map(tm -> tm.getTrip().getId())
        .toList();
  }
}
