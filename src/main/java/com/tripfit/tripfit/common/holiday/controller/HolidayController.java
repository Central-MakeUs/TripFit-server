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

@Tag(name = "Holiday", description = "대한민국 공휴일 날짜 조회. 캘린더 화면 표시 지원")
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {
  private final HolidayQueryService holidayQueryService;

  public HolidayController(HolidayQueryService holidayQueryService) {
    this.holidayQueryService = holidayQueryService;
  }

  /**
   * [구간 내 공휴일 날짜 목록]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 마이페이지나 여행방 어느 캘린더 화면에서 호출하든 동일한 결과를 반환하는 공휴일 참조 데이터입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - DB/캐시에 적재된 공휴일 테이블에서 지정된 startDate ~ endDate 구간의 공휴일 날짜 리스트를 반환합니다.
   */
  @Operation(summary = "구간 내 공휴일 날짜 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_INPUT (startDate가 endDate보다 뒤)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  ResponseEntity<SuccessResponse<HolidayListResponse>> getHolidays(
      @Parameter(description = "조회 시작일(포함)", example = "2027-01-01") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "조회 종료일(포함)", example = "2027-01-31") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        SuccessResponse.of(holidayQueryService.getHolidays(startDate, endDate)));
  }
}
