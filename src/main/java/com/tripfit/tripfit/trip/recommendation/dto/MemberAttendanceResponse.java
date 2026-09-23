package com.tripfit.tripfit.trip.recommendation.dto;

import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    개별 추천 후보의 참여자별 참석 세부 정보(Breakdown)입니다.
    - 미응답 참여자는 포함되지 않습니다.
    """)
public record MemberAttendanceResponse(
    @Schema(
        description = "참여자 표시 이름입니다. (동명이인은 `이름(2)` 형식)",
        example = "김유정") String name,

    @Schema(description = "참석 분류 상태입니다.") AttendanceType attendance,

    @Schema(
        description = "해당 추천 기간 내에 불확실로 표시한 일수입니다.",
        example = "2") int uncertainDays,

    @Schema(
        description = "이 후보로 확정할 경우 필요한 연차 일수입니다. (반차는 0.5일로 계산)",
        example = "2.0") double vacationDaysNeeded
) {
}
