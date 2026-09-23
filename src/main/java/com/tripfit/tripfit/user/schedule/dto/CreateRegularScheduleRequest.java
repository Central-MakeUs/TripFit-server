package com.tripfit.tripfit.user.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Schema(description = """
    정기 일정 생성 요청입니다. (POST /users/schedule/regular)
    - 입력된 시작(start)과 종료(end) 시각을 기반으로 오전/오후/저녁 슬롯을 자동 계산합니다.
    """)
public record CreateRegularScheduleRequest(
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
