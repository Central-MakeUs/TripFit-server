package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    연차 및 휴일 정보 전체 교체 요청입니다. (PATCH /users/schedule/vacation-policy)
    - 부분 수정(Patch)이 불가능하며, 4개 필드 모두 필수로 포함되어야 합니다.
    """)
public record UpdateVacationPolicyRequest(
    @Schema(
        description = """
            여행당 허용할 최대 연차 일수입니다.
            - 0에서 10 사이의 값만 허용됩니다.
            """,
        example = "2",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(10) Integer maxVacationDays,

    @Schema(
        description = """
            연차 신청 가능 시점(사전 신청일)입니다.
            - 이 값이 저장되면 '사전 일정 입력 완료(hasCompletedPreSchedule)'로 판정됩니다.
            - 필수 입력 항목입니다.
            """,
        example = "ONE_WEEK_BEFORE",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull VacationApplyPeriod vacationApplyPeriod,

    @Schema(
        description = "반차 사용 가능 여부입니다.",
        example = "false",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean halfVacationAvailable,

    @Schema(
        description = "공휴일 휴무 여부입니다.",
        example = "true",
        nullable = false,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean holidayRest
) {
}
