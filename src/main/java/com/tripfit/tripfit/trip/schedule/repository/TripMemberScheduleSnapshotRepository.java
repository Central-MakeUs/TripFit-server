package com.tripfit.tripfit.trip.schedule.repository;

import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripMemberScheduleSnapshotRepository
    extends JpaRepository<TripMemberScheduleSnapshot, UUID> {

  boolean existsByTrip_Id(UUID tripId);

  List<TripMemberScheduleSnapshot> findByTrip_IdOrderByUser_IdAscScheduleDateAsc(UUID tripId);

  // unconfirm 시 스냅샷 폐기 — 재확정 전까지 라이브 데이터로 되돌림(trip-recommendation.md unconfirm 절)
  @Modifying
  @Query("DELETE FROM TripMemberScheduleSnapshot s WHERE s.trip.id = :tripId")
  void deleteByTripId(@Param("tripId") UUID tripId);
}
