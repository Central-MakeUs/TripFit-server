package com.tripfit.tripfit.trip.recommendation.repository;

import com.tripfit.tripfit.trip.recommendation.domain.Recommendation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  @Modifying
  @Query("DELETE FROM Recommendation r WHERE r.trip.id = :tripId")
  void deleteByTripId(@Param("tripId") UUID tripId);

  List<Recommendation> findByTrip_IdOrderByRankAsc(UUID tripId);

  Optional<Recommendation> findByTrip_IdAndRank(UUID tripId, Integer rank);
}
