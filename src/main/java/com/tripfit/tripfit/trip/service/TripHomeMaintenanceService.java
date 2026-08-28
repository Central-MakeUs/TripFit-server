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

  @Transactional
  public void runForDate(LocalDate today) {

    List<Trip> expired = tripRepository.findExpiredOngoing(today);
    for (Trip trip : expired) {
      tripScheduleSnapshotService.freezeTrip(trip);
      trip.expire();
    }

    tripMemberRepository.clearExpiredPins(today);
  }
}
