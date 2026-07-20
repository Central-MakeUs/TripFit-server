package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #27 S1~S3 — `end_range` 경과 trip TERMINATED DB 반영 · Pin 일괄 해제. */
@Service
public class TripHomeMaintenanceService {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  TripHomeMaintenanceService(
      TripRepository tripRepository, TripMemberRepository tripMemberRepository) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
  }

  @Transactional
  public void runForDate(LocalDate today) {
    // 1. ONGOING + end_range 경과 → TERMINATED
    tripRepository.terminateExpiredOngoing(today);
    // 2. 동일 end_range 조건 Pin 해제 (soft-deleted trip·member 제외)
    tripMemberRepository.clearExpiredPins(today);
  }
}
