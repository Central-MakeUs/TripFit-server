package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.TripMemberRole;
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
// trip API facade — Command / Query / MemberQuery에 위임
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

  // facade: 여행방 생성 → TripCommandService
  public CreateTripResponse createTrip(UUID userId, CreateTripRequest request) {
    return tripCommandService.createTrip(userId, request);
  }

  // facade: 내 여행방 목록 → TripQueryService
  public TripListResponse listMyTrips(UUID userId, TripListQuery query) {
    return tripQueryService.listMyTrips(userId, query);
  }

  // facade: 여행방 상세 → TripQueryService
  public TripDetailResponse getTrip(UUID tripId, UUID userId) {
    return tripQueryService.getTrip(tripId, userId);
  }

  // facade: 여행방 메타 수정 → TripCommandService
  public TripDetailResponse patchTrip(UUID tripId, UUID userId, PatchTripRequest request) {
    return tripCommandService.patchTrip(tripId, userId, request);
  }

  // facade: 여행방 삭제 → TripCommandService
  public void deleteTrip(UUID tripId, UUID userId) {
    tripCommandService.deleteTrip(tripId, userId);
  }

  // facade: 초대코드 참여 → TripCommandService
  public TripDetailResponse joinTrip(UUID userId, JoinTripRequest request) {
    return tripCommandService.joinTrip(userId, request);
  }

  // facade: 방장 일정 확인(JOINED→RESPONDED) → TripCommandService
  public TripDetailResponse confirmSchedule(UUID tripId, UUID userId) {
    return tripCommandService.confirmSchedule(tripId, userId);
  }

  // facade: Pin on/off → TripCommandService
  public TripDetailResponse updatePin(UUID tripId, UUID userId, UpdateTripPinRequest request) {
    return tripCommandService.updatePin(tripId, userId, request);
  }

  // facade: 멤버 목록 → TripMemberQueryService
  public TripMembersResponse listMembers(UUID tripId, UUID userId) {
    return tripMemberQueryService.listMembers(tripId, userId);
  }

  // facade: 멤버별 일정 달력(live/snapshot) → TripMemberQueryService
  public MemberScheduleCalendarResponse getMemberScheduleCalendar(UUID tripId, UUID userId) {
    return tripMemberQueryService.getMemberScheduleCalendar(tripId, userId);
  }

  // facade: 참여자 내보내기 → TripCommandService
  public TripMembersResponse removeMember(UUID tripId, UUID ownerId, UUID targetUserId) {
    return tripCommandService.removeMember(tripId, ownerId, targetUserId);
  }

  // facade: 멤버 자진 나가기 → TripCommandService
  public void leaveTrip(UUID tripId, UUID userId) {
    tripCommandService.leaveTrip(tripId, userId);
  }

  // 회원 탈퇴 cascade — MEMBER인 활성 방 전부 자진 나가기 처리(각 leaveTrip 호출이 프록시를 거치도록 파사드에서 반복)
  public void leaveAllActiveTripsAsMember(UUID userId) {
    for (UUID tripId : tripQueryService.listActiveTripIdsByRole(userId, TripMemberRole.MEMBER)) {
      tripCommandService.leaveTrip(tripId, userId);
    }
  }

  // 회원 탈퇴 cascade — OWNER인 활성 방 전부 삭제 처리(각 deleteTrip 호출이 프록시를 거치도록 파사드에서 반복)
  public void deleteAllOwnedActiveTrips(UUID userId) {
    for (UUID tripId : tripQueryService.listActiveTripIdsByRole(userId, TripMemberRole.OWNER)) {
      tripCommandService.deleteTrip(tripId, userId);
    }
  }
}
