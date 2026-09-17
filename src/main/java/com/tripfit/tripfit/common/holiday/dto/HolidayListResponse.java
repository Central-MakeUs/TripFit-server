package com.tripfit.tripfit.common.holiday.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "구간 내 대한민국 공휴일 날짜 목록. GET /api/v1/holidays")
public record HolidayListResponse(
    @Schema(description = "조회 시작일 (요청값 echo)", example = "2027-01-01") LocalDate startDate,

    @Schema(description = "조회 종료일 (요청값 echo)", example = "2027-01-31") LocalDate endDate,

    @Schema(description = "구간 내 공휴일 날짜(대체공휴일 포함), 오름차순 정렬") List<LocalDate> holidays
) {
}
