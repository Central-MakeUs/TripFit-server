package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #13 추천·확정 유스케이스 — API·계산 로직은 #13. #38 freeze는 CONFIRMED 전환과 동일 TX. */
@Service
class TripRecommendationService {

  private final TripServiceSupport support;

  private final TripScheduleSnapshotService tripScheduleSnapshotService;

  TripRecommendationService(
      TripServiceSupport support, TripScheduleSnapshotService tripScheduleSnapshotService) {
    this.support = support;
    this.tripScheduleSnapshotService = tripScheduleSnapshotService;
  }

  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void generateRecommendations(UUID tripId, UUID ownerId, RecommendationMode mode) {
    // TODO #13: BR-TRIP-005 — hard DELETE · TOP 3 INSERT · last_recommendation_mode
  }

  // #13: 추천 rank/기간 확정. #38: status=CONFIRMED 직후 freezeTrip (R-freeze · 공백 불허)
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void confirmSchedule(UUID tripId, UUID ownerId) {
    Trip trip = support.requireActiveTrip(tripId);
    support.requireOwner(trip, ownerId);
    support.requireOngoingForMutation(trip);
    // TODO #13: recommendationRank / startDate·endDate → confirmed_* 설정
    trip.setStatus(TripStatus.CONFIRMED);
    tripScheduleSnapshotService.freezeTrip(trip);
  }
}
