package com.tripfit.tripfit.trip.dto;

import com.tripfit.tripfit.trip.domain.AttendanceType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 근거 상세의 참여자별 브레이크다운 한 행. 미응답 참여자는 포함하지 않음")
// @formatter:off
public record MemberAttendanceResponse(
    @Schema(description = "참여자 표시명 (동명이인은 `이름(2)` 형식)", example = "김유정") String name,

    @Schema(description = "참석 분류") AttendanceType attendance,

    @Schema(description = "이 후보 기간 내 불확실로 표시한 날짜 수", example = "2") int uncertainDays,

    @Schema(description = "이 후보로 확정 시 필요한 연차 일수 (반차=0.5일)", example = "2.0") double vacationDaysNeeded
) {
}
// @formatter:on
