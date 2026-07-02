package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.TripMember;
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

@Service
class TripQueryService {

  private final TripMemberRepository tripMemberRepository;

  private final TripServiceSupport support;

  TripQueryService(TripMemberRepository tripMemberRepository, TripServiceSupport support) {
    this.tripMemberRepository = tripMemberRepository;
    this.support = support;
  }

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
                  int memberCount = counts == null ? 0 : (int) counts.getMemberCount();
                  int respondedCount = counts == null ? 0 : (int) counts.getRespondedCount();
                  return support.toHomeCard(
                      m.getTrip(),
                      m,
                      memberCount,
                      respondedCount,
                      previewsByTripId.getOrDefault(tripId, List.of()));
                })
            .toList();
    return new TripListResponse(trips);
  }

  @Transactional(readOnly = true)
  public TripDetailResponse getTrip(UUID tripId, UUID userId) {
    TripMember membership = support.requireActiveMember(tripId, userId);
    return support.toDetail(membership.getTrip(), membership);
  }

  TripDetailResponse toDetail(com.tripfit.tripfit.trip.domain.Trip trip, TripMember membership) {
    return support.toDetail(trip, membership);
  }
}
