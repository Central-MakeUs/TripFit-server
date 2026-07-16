package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #27 S1~S3 + #38 — end_range 경과 → freeze → TERMINATED · Pin 해제 (동일 TX). */
@Service
public class TripHomeMaintenanceService {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripScheduleSnapshotService tripScheduleSnapshotService;

  TripHomeMaintenanceService(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      TripScheduleSnapshotService tripScheduleSnapshotService) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.tripScheduleSnapshotService = tripScheduleSnapshotService;
  }

  // 일 배치: 만료 ONGOING freeze→TERMINATED + Pin 해제 (#27·#38)
  @Transactional
  public void runForDate(LocalDate today) {
    // 1. 만료 ONGOING 로드 → effective freeze → TERMINATED (R-freeze · R-gap 공백 불허)
    List<Trip> expired = tripRepository.findExpiredOngoing(today);
    for (Trip trip : expired) {
      tripScheduleSnapshotService.freezeTrip(trip);
      trip.setStatus(TripStatus.TERMINATED);
    }
    // 2. Pin 해제 (soft-deleted trip·member 제외)
    tripMemberRepository.clearExpiredPins(today);
  }
}
