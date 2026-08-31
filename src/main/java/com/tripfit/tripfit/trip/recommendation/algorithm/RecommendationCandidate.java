package com.tripfit.tripfit.trip.recommendation.algorithm;

import java.time.LocalDate;

// #50 RecommendationEngine 계산 결과 1건 — 저장은 TripRecommendationService가 Recommendation 엔티티로 매핑
public record RecommendationCandidate(
    LocalDate startDate,
    LocalDate endDate,
    int attendRate,
    int partialAttendCount,
    int uncertainCount,
    double totalVacationDays,
    double score,
    int uncertainScheduleCount
) {
}
