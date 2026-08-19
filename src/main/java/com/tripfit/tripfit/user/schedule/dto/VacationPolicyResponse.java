package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "연차·휴일 정보 응답. GET/PATCH /users/schedule/vacation-policy")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record VacationPolicyResponse(
    @Schema(description = "여행당 최대 연차 일수 (기본 2, 허용 0~10)", example = "2")
    int maxVacationDays,

    @Schema(description = "연차 신청 가능 시점. 미설정 시 null", example = "ONE_WEEK_BEFORE", nullable = true)
    VacationApplyPeriod vacationApplyPeriod,

    @Schema(description = "반차 사용 가능 여부 (기본 false)", example = "false")
    boolean halfVacationAvailable,

    @Schema(description = "공휴일 휴무 여부 (기본 true)", example = "true")
    boolean holidayRest
) {
}
// @formatter:on
