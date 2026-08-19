package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(
    description = "연차·반차·공휴일 휴무 설정 전체 교체 요청. PATCH /users/schedule/vacation-policy. 부분 patch 아님 — 생략(null) 필드는 기본값으로 대체")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record UpdateVacationPolicyRequest(
    @Schema(
        description = "여행당 최대 연차 일수. 생략 시 2, 허용 0~10",
        example = "2",
        nullable = true)
    @Min(0)
    @Max(10)
    Integer maxVacationDays,

    @Schema(
        description = "연차 신청 가능 시점. 생략 시 null",
        example = "ONE_WEEK_BEFORE",
        nullable = true)
    VacationApplyPeriod vacationApplyPeriod,

    @Schema(description = "반차 사용 가능 여부. 생략 시 false", example = "false", nullable = true)
    Boolean halfVacationAvailable,

    @Schema(description = "공휴일 휴무 여부. 생략 시 true", example = "true", nullable = true)
    Boolean holidayRest
) {
}
// @formatter:on
