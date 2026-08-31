package com.tripfit.tripfit.trip.recommendation.repository;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationFeedbackRepository
    extends JpaRepository<RecommendationFeedback, UUID> {

  // upsert 조회 SSOT — 후보(recommendation)당 최대 1건
  Optional<RecommendationFeedback> findByRecommendationId(UUID recommendationId);
}
