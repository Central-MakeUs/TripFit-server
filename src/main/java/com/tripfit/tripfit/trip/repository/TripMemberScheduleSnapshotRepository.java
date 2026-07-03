package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.TripMemberScheduleSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberScheduleSnapshotRepository
    extends JpaRepository<TripMemberScheduleSnapshot, UUID> {

  boolean existsByTrip_Id(UUID tripId);

  List<TripMemberScheduleSnapshot> findByTrip_IdOrderByUser_IdAscScheduleDateAsc(UUID tripId);
}
