package com.tripfit.tripfit.trip.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.trip.schedule.service.TripScheduleSnapshotService;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripHomeMaintenanceService {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final TripScheduleSnapshotService tripScheduleSnapshotService;

  // 스케줄러(cron) 등에 의해 주기적으로 호출되어 여행방 상태를 갱신합니다.
  @Transactional
  public void runForDate(LocalDate today) {

    // 1. 종료일이 지난 진행 중(ONGOING)인 여행방 목록을 조회합니다.
    List<Trip> expired = tripRepository.findExpiredOngoing(today);

    // 2. 각 만료된 여행방에 대해 현재 스케줄 상태를 스냅샷으로 백업(Freeze)하고, 상태를 EXPIRED로 변경합니다.
    for (Trip trip : expired) {
      tripScheduleSnapshotService.freezeTrip(trip);
      trip.expire();
    }

    tripMemberRepository.clearExpiredPins(today);
  }
}
