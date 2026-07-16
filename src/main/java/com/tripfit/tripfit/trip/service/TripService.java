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
// trip API facade — Command/Query/MemberQuery에 위임
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

  // facade: 여행방 생성
  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
    return tripCommandService.createTrip(userId, request);
  }

  // facade: 내 여행방 목록
  public TripListResponse listMyTrips(UUID userId, TripListQuery query) {
    return tripQueryService.listMyTrips(userId, query);
  }

  // facade: 여행방 상세
  public TripDetailResponse getTrip(UUID tripId, UUID userId) {
    return tripQueryService.getTrip(tripId, userId);
  }

  // facade: 여행방 메타 수정
  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    return tripCommandService.patchTrip(tripId, userId, request);
  }

  // facade: 여행방 삭제
  public void deleteTrip(UUID tripId, UUID userId) {
    tripCommandService.deleteTrip(tripId, userId);
  }

  // facade: 초대코드 참여
  public TripDetailResponse joinTrip(UUID userId, JoinTripRequest request) {
    return tripCommandService.joinTrip(userId, request);
  }

  // facade: 방장 일정 확인 (JOINED→RESPONDED)
  public TripDetailResponse confirmSchedule(UUID tripId, UUID userId) {
    return tripCommandService.confirmSchedule(tripId, userId);
  }

  // facade: Pin on/off
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    return tripCommandService.updatePin(tripId, userId, request);
  }

  // facade: 멤버 목록
  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    return tripMemberQueryService.listMembers(tripId, userId);
  }

  // facade: 멤버별 일정 달력 (#37·#38)
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    return tripMemberQueryService.getMemberScheduleCalendar(tripId, userId);
  }

  // facade: 참여자 내보내기 (#20)
  public TripMembersResponse removeMember(UUID tripId, UUID ownerId, UUID targetUserId) {
    return tripCommandService.removeMember(tripId, ownerId, targetUserId);
  }
}
