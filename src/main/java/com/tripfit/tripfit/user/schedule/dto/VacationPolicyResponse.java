package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "연차 및 휴일 정보 응답입니다. (GET/PATCH /users/schedule/vacation-policy)")
public record VacationPolicyResponse(
    @Schema(
        description = """
            여행당 허용된 최대 연차 일수입니다.
            - 허용 범위: 0~10 (기본값: 2)
            """,
        example = "2") int maxVacationDays,

    @Schema(
        description = """
            연차 신청 가능 시점입니다.
            - 미설정 시 null을 반환합니다.
            """,
        example = "ONE_WEEK_BEFORE",
        nullable = true) VacationApplyPeriod vacationApplyPeriod,

    @Schema(
        description = "반차 사용 가능 여부입니다. (기본값: false)",
        example = "false") boolean halfVacationAvailable,

    @Schema(
        description = "공휴일 휴무 여부입니다. (기본값: true)",
        example = "true") boolean holidayRest
) {
}
