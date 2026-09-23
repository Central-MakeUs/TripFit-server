package com.tripfit.tripfit.user.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Schema(description = """
    정기 일정 전체 수정 요청입니다. (PATCH /users/schedule/regular/{id})
    - 시작(start) 또는 종료(end) 시각이 변경될 경우 슬롯 상태가 자동 재계산됩니다.
    """)
public record UpdateRegularScheduleRequest(
    @Schema(
        description = "화면에 표시될 일정 이름입니다. (예: 출근, 수업, 회의 등)",
        example = "출근",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String title,

    @Schema(
        description = """
            반복할 요일 목록입니다. (콤마로 구분)
            - 예: MON,TUE,WED,THU,FRI
            - 생략 시 null로 처리됩니다.
            """,
        example = "MON,TUE,WED,THU,FRI",
        nullable = true) String daysOfWeek,

    @Schema(
        description = "일정 시작 시각입니다.",
        example = "09:00:00",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime startTime,

    @Schema(
        description = "일정 종료 시각입니다.",
        example = "18:00:00",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime endTime
) {
}
