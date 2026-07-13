package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.TripMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

  boolean existsByTripIdAndUserId(UUID tripId, UUID userId);

  Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

  List<TripMember> findByTripId(UUID tripId);
}
