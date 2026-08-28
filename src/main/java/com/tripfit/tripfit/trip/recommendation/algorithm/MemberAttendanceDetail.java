package com.tripfit.tripfit.trip.recommendation.algorithm;

import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import java.util.UUID;

public record MemberAttendanceDetail(
    UUID userId,
    AttendanceType attendance,
    int uncertainDays,
    double vacationDays
) {
}
