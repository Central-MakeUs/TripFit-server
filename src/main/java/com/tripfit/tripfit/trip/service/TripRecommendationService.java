package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.config.TripActivity;
import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추천 후보 생성·여행 날짜 확정. 확정 시 멤버 일정 스냅샷도 같은 TX에서 고정한다. */
@Service
class TripRecommendationService {

  private final TripServiceSupport support;

  private final TripScheduleSnapshotService tripScheduleSnapshotService;

  TripRecommendationService(
      TripServiceSupport support, TripScheduleSnapshotService tripScheduleSnapshotService) {
    this.support = support;
    this.tripScheduleSnapshotService = tripScheduleSnapshotService;
  }

  // 방장이 추천 모드로 TOP3 후보를 생성한다 (미구현 stub)
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void generateRecommendations(UUID tripId, UUID ownerId, RecommendationMode mode) {
    // TODO: 기존 추천 hard DELETE 후 TOP3 INSERT, lastRecommendationMode 갱신
    // 상세: docs/specs/trip-recommendation.md (#13)
  }

  // 방장이 추천 후보(또는 직접 날짜)로 여행을 확정한다 — CONFIRMED 직후 일정 스냅샷 freeze
  @Transactional
  @TripActivity(tripIdParam = "tripId")
  public void confirmSchedule(UUID tripId, UUID ownerId) {
    Trip trip = support.requireActiveTrip(tripId);
    support.requireOwner(trip, ownerId);
    support.requireOngoingForMutation(trip);
    // TODO: recommendationRank / startDate·endDate → confirmed_* 설정
    // 상세: docs/specs/trip-recommendation.md (#13)
    trip.setStatus(TripStatus.CONFIRMED);
    tripScheduleSnapshotService.freezeTrip(trip);
  }
}
