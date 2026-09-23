package com.tripfit.tripfit.common.holiday.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = """
    요청 구간 내 대한민국의 공휴일 날짜 목록입니다. (GET /api/v1/holidays)
    """)
public record HolidayListResponse(
    @Schema(
        description = """
            조율 시작일입니다.
            - 요청값을 그대로 반환합니다.
            """,
        example = "2027-01-01") LocalDate startDate,

    @Schema(
        description = """
            조율 종료일입니다.
            - 요청값을 그대로 반환합니다.
            """,
        example = "2027-01-31") LocalDate endDate,

    @Schema(
        description = """
            구간 내 공휴일(대체공휴일 포함) 날짜 목록입니다.
            - 오름차순으로 정렬되어 반환됩니다.
            """) List<LocalDate> holidays
) {
}
