package com.tripfit.tripfit.trip.recommendation.algorithm;

import java.time.LocalDate;

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
