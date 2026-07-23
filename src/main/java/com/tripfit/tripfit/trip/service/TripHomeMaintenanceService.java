package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 유지보수 배치 — 희망 기간이 지난 조율 중 방을 종료하고, 만료 Pin을 해제한다. */
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

  // 일 배치: endRange 지난 ONGOING을 스냅샷 고정 후 TERMINATED로 바꾸고, 만료 Pin을 해제한다
  @Transactional
  public void runForDate(LocalDate today) {
    // 1. 만료 ONGOING 로드 → 일정 스냅샷 freeze → TERMINATED (확정·종료 공백 없이 고정)
    List<Trip> expired = tripRepository.findExpiredOngoing(today);
    for (Trip trip : expired) {
      tripScheduleSnapshotService.freezeTrip(trip);
      trip.setStatus(TripStatus.TERMINATED);
    }
    // 2. Pin 해제 (soft-deleted trip·member 제외)
    tripMemberRepository.clearExpiredPins(today);
  }
}
