package com.tripfit.tripfit.common.holiday.controller;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.common.holiday.dto.HolidayListResponse;
import com.tripfit.tripfit.common.holiday.service.HolidayQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Holiday", description = "대한민국 공휴일 날짜를 조회하여 캘린더 화면 표시를 지원합니다.")
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {
  private final HolidayQueryService holidayQueryService;

  public HolidayController(HolidayQueryService holidayQueryService) {
    this.holidayQueryService = holidayQueryService;
  }

  /**
   * [구간 내 공휴일 날짜 목록] 지정된 구간 내의 대한민국 공휴일 날짜 리스트를 반환합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 캘린더 화면 표시에 참조할 공통 데이터입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - DB/캐시에 적재된 공휴일 테이블을 기반으로 조회하여 반환합니다.
   */
  @Operation(summary = "구간 내 공휴일 날짜 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "시작일(startDate)이 종료일(endDate)보다 뒤에 있습니다(INVALID_INPUT).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰이 없거나, 무효하거나(AUTH_INVALID_TOKEN), 만료되었습니다(AUTH_EXPIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  ResponseEntity<SuccessResponse<HolidayListResponse>> getHolidays(
      @Parameter(description = "조회 시작일입니다. (해당 날짜 포함)") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "조회 종료일입니다. (해당 날짜 포함)") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        SuccessResponse.of(holidayQueryService.getHolidays(startDate, endDate)));
  }
}
