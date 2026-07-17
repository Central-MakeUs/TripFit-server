package com.tripfit.tripfit.trip.repository;

import com.tripfit.tripfit.trip.domain.Recommendation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  // BR-TRIP-010: 희망 기간·일수 변경 시 recommendation hard DELETE (#13 연동)
  @Modifying
  @Query("DELETE FROM Recommendation r WHERE r.trip.id = :tripId")
  void deleteByTripId(@Param("tripId") UUID tripId);
}
