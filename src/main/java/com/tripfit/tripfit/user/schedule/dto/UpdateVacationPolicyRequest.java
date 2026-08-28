package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(
    description = "연차·휴일 정보 전체 교체 요청. PATCH /users/schedule/vacation-policy. 부분 patch 아님. 4개 값 모두 필수")

public record UpdateVacationPolicyRequest(
    @Schema(
        description = "여행당 최대 연차 일수. 허용 0~10",
        example = "2",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(10) Integer maxVacationDays,

    @Schema(
        description = "연차 신청 가능 시점(사전 신청일). 저장되면 사전 일정 입력 완료로 판정된다. 생략 불가",
        example = "ONE_WEEK_BEFORE",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull VacationApplyPeriod vacationApplyPeriod,

    @Schema(
        description = "반차 사용 가능 여부",
        example = "false",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean halfVacationAvailable,

    @Schema(
        description = "공휴일 휴무 여부",
        example = "true",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean holidayRest
) {
}
