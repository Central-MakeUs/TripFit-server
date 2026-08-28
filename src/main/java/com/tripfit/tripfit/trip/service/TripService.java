package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.membership.service.TripMemberQueryService;
import com.tripfit.tripfit.trip.recommendation.service.TripRecommendationService;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.recommendation.dto.ConfirmTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationListResponse;
import com.tripfit.tripfit.trip.recommendation.dto.SaveRecommendationFeedbackRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.recommendation.dto.UnconfirmTripRequest;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service

public class TripService {

  private final TripCommandService tripCommandService;

  private final TripQueryService tripQueryService;

  private final TripMemberQueryService tripMemberQueryService;

  private final TripRecommendationService tripRecommendationService;

  public TripEntryResponse createTrip(UUID userId, CreateTripRequest request) {
    return tripCommandService.createTrip(userId, request);
  }

  public TripListResponse listMyTrips(UUID userId, TripListQuery query) {
    return tripQueryService.listMyTrips(userId, query);
  }

  public TripDetailResponse getTrip(UUID tripId, UUID userId) {
    return tripQueryService.getTrip(tripId, userId);
  }

  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    return tripCommandService.patchTrip(tripId, userId, request);
  }

  public void deleteTrip(UUID tripId, UUID userId) {
    tripCommandService.deleteTrip(tripId, userId);
  }

  public TripEntryResponse joinTrip(UUID userId, JoinTripRequest request) {
    return tripCommandService.joinTrip(userId, request);
  }

  public TripDetailResponse activateMembership(UUID tripId, UUID userId) {
    return tripCommandService.activateMembership(tripId, userId);
  }

  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    return tripCommandService.updatePin(tripId, userId, request);
  }

  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    return tripMemberQueryService.listMembers(tripId, userId);
  }

  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    return tripMemberQueryService.getMemberScheduleCalendar(tripId, userId);
  }

  public TripMembersResponse removeMember(UUID tripId, UUID ownerId, UUID targetUserId) {
    return tripCommandService.removeMember(tripId, ownerId, targetUserId);
  }

  public void leaveTrip(UUID tripId, UUID userId) {
    tripCommandService.leaveTrip(tripId, userId);
  }

  public void leaveAllActiveTripsAsMember(UUID userId) {
    for (UUID tripId : tripQueryService.listActiveTripIdsByRole(userId, TripMemberRole.MEMBER)) {
      tripCommandService.leaveTrip(tripId, userId);
    }
  }

  public void deleteAllOwnedActiveTrips(UUID userId) {
    for (UUID tripId : tripQueryService.listActiveTripIdsByRole(userId, TripMemberRole.OWNER)) {
      tripCommandService.deleteTrip(tripId, userId);
    }
  }

  public RecommendationListResponse generateRecommendations(
      UUID tripId,
      UUID ownerId,
      RecommendationMode mode) {
    return tripRecommendationService.generateRecommendations(tripId, ownerId, mode);
  }

  public RecommendationListResponse listRecommendations(UUID tripId, UUID ownerId) {
    return tripRecommendationService.listRecommendations(tripId, ownerId);
  }

  public RecommendationDetailResponse getRecommendationDetail(UUID tripId, UUID ownerId, int rank) {
    return tripRecommendationService.getRecommendationDetail(tripId, ownerId, rank);
  }

  public void saveRecommendationFeedback(
      UUID tripId,
      UUID ownerId,
      int rank,
      SaveRecommendationFeedbackRequest request) {
    tripRecommendationService.saveFeedback(tripId, ownerId, rank, request);
  }

  public TripDetailResponse confirmSchedule(UUID tripId, UUID ownerId, ConfirmTripRequest request) {
    return tripRecommendationService.confirmSchedule(tripId, ownerId, request);
  }

  public void unconfirm(UUID tripId, UUID ownerId, UnconfirmTripRequest request) {
    tripRecommendationService.unconfirm(tripId, ownerId, request);
  }
}
