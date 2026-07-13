package com.tripfit.tripfit.user.schedule.dto;

import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Schema(description = "정기 일정 생성 요청. start/end로 슬롯을 계산")
// @formatter:off — record 컴포넌트는 Eclipse가 parameter로 취급해 컨트롤러 한 줄 스타일과 충돌
public record CreateRegularScheduleRequest(
    @Schema(
        description = "표시명 (출근·수업·회의 등)",
        example = "출근",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    String title,

    @Schema(description = "반복 요일. 콤마 구분", example = "MON,TUE,WED,THU,FRI", nullable = true)
    String daysOfWeek,

    @Schema(
        description = "시작 시각",
        example = "09:00:00",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    LocalTime startTime,

    @Schema(
        description = "종료 시각",
        example = "18:00:00",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    LocalTime endTime,

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

    @Schema(description = "반차 사용 가능 여부. 생략 시 false(N)", example = "false", nullable = true)
    Boolean halfVacationAvailable,

    @Schema(description = "공휴일 휴무 여부. 생략 시 true(Y)", example = "true", nullable = true)
    Boolean holidayRest
) {
}
// @formatter:on
