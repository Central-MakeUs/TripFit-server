package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TripService {

  private final TripCommandService tripCommandService;

  private final TripQueryService tripQueryService;

  private final TripMemberQueryService tripMemberQueryService;

  public TripService(
      TripCommandService tripCommandService,
      TripQueryService tripQueryService,
      TripMemberQueryService tripMemberQueryService) {
    this.tripCommandService = tripCommandService;
    this.tripQueryService = tripQueryService;
    this.tripMemberQueryService = tripMemberQueryService;
  }

  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
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

  public TripDetailResponse joinTrip(UUID userId, JoinTripRequest request) {
    return tripCommandService.joinTrip(userId, request);
  }

  public TripDetailResponse confirmSchedule(UUID tripId, UUID userId) {
    return tripCommandService.confirmSchedule(tripId, userId);
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
}
