package com.tripfit.tripfit.trip.recommendation.algorithm;

import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import java.util.UUID;

// 특정 후보 구간 하나에 대한 참여자 1인의 분류 결과 — GET .../recommendations/{rank} 상세·confirm 시점 통계 공용
public record MemberAttendanceDetail(
    UUID userId,
    AttendanceType attendance,
    int uncertainDays,
    double vacationDays
) {
}
