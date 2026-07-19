package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Trip;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerId);

  Optional<Trip> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

  boolean existsByInviteCode(String inviteCode);

  @Modifying
  @Query("""
      UPDATE Trip t SET t.status = com.tripfit.tripfit.trip.domain.TripStatus.TERMINATED
      WHERE t.deletedAt IS NULL
      AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
      AND t.endRange < :today
      """)
  int terminateExpiredOngoing(@Param("today") LocalDate today);
}
