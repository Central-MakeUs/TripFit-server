package com.tripfit.tripfit.trip.recommendation.repository;

import com.tripfit.tripfit.trip.recommendation.domain.RecommendationFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationFeedbackRepository
    extends JpaRepository<RecommendationFeedback, UUID> {

  Optional<RecommendationFeedback> findByRecommendationId(UUID recommendationId);
}
