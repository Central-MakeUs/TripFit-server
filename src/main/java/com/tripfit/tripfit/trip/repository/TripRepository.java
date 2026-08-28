package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Trip;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerId);

  boolean existsByInviteCode(String inviteCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Trip t WHERE t.inviteCode = :inviteCode AND t.deletedAt IS NULL")
  Optional<Trip> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

  @Query("""
      SELECT t FROM Trip t
      WHERE t.deletedAt IS NULL
      AND t.status = com.tripfit.tripfit.trip.domain.TripStatus.ONGOING
      AND t.endRange < :today
      """)
  List<Trip> findExpiredOngoing(@Param("today") LocalDate today);
}
