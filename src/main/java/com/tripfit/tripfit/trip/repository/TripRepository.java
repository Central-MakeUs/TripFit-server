package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Trip;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  Optional<Trip> findByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndDeletedAtIsNull(UUID id);

  boolean existsByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerId);

  Optional<Trip> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

  boolean existsByInviteCode(String inviteCode);
}
