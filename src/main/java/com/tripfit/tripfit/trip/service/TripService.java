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
// trip API facade — Command / Query / MemberQuery / Recommendation에 위임
public class TripService {

  private final TripCommandService tripCommandService;

  private final TripQueryService tripQueryService;

  private final TripMemberQueryService tripMemberQueryService;

  private final TripRecommendationService tripRecommendationService;

  // facade: 여행방 생성 → TripCommandService
  public TripEntryResponse createTrip(UUID userId, CreateTripRequest request) {
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
  public TripEntryResponse joinTrip(UUID userId, JoinTripRequest request) {
    return tripCommandService.joinTrip(userId, request);
  }

  // facade: 멤버십 activate(SCHEDULE_PENDING→ACTIVE) → TripCommandService
  public TripDetailResponse activateMembership(UUID tripId, UUID userId) {
    return tripCommandService.activateMembership(tripId, userId);
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

  // facade: 추천 TOP3 재계산 → TripRecommendationService
  public RecommendationListResponse generateRecommendations(
      UUID tripId,
      UUID ownerId,
      RecommendationMode mode) {
    return tripRecommendationService.generateRecommendations(tripId, ownerId, mode);
  }

  // facade: 저장된 추천 TOP3 조회 → TripRecommendationService
  public RecommendationListResponse listRecommendations(UUID tripId, UUID ownerId) {
    return tripRecommendationService.listRecommendations(tripId, ownerId);
  }

  // facade: 추천 근거 상세 → TripRecommendationService
  public RecommendationDetailResponse getRecommendationDetail(UUID tripId, UUID ownerId, int rank) {
    return tripRecommendationService.getRecommendationDetail(tripId, ownerId, rank);
  }

  // facade: 추천 피드백 upsert → TripRecommendationService
  public void saveRecommendationFeedback(
      UUID tripId,
      UUID ownerId,
      int rank,
      SaveRecommendationFeedbackRequest request) {
    tripRecommendationService.saveFeedback(tripId, ownerId, rank, request);
  }

  // facade: 일정 확정 → TripRecommendationService
  public TripDetailResponse confirmSchedule(UUID tripId, UUID ownerId, ConfirmTripRequest request) {
    return tripRecommendationService.confirmSchedule(tripId, ownerId, request);
  }

  // facade: 확정 취소 → TripRecommendationService
  public void unconfirm(UUID tripId, UUID ownerId, UnconfirmTripRequest request) {
    tripRecommendationService.unconfirm(tripId, ownerId, request);
  }
}
