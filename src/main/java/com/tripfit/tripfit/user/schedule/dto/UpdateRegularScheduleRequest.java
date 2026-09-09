package com.tripfit.tripfit.user.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Schema(
    description = "정기 일정 전체 수정 요청. PATCH /users/schedule/regular/{id}. start/end 변경 시 슬롯 재계산")
// @formatter:off — record 컴포넌트는 Eclipse가 parameter로 취급해 컨트롤러 한 줄 스타일과 충돌
public record UpdateRegularScheduleRequest(
    @Schema(
        description = "표시명 (출근·수업·회의 등)",
        example = "출근",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    String title,

    @Schema(
        description = "반복 요일. Weekday 콤마 구분(MON~SUN). 생략 시 null",
        example = "MON,TUE,WED,THU,FRI",
        nullable = true)
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
    LocalTime endTime
) {
}
// @formatter:on
