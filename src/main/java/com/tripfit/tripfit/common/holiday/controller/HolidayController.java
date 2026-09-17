package com.tripfit.tripfit.common.holiday.controller;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.common.holiday.dto.HolidayListResponse;
import com.tripfit.tripfit.common.holiday.service.HolidayQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "Holiday", description = "대한민국 공휴일 날짜 조회 — 캘린더 화면 표시 지원")
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

  private final HolidayQueryService holidayQueryService;

  public HolidayController(HolidayQueryService holidayQueryService) {
    this.holidayQueryService = holidayQueryService;
  }

  /** 정기 일정·개별 일정과 무관한 순수 참조 데이터라, 마이페이지·여행방 어느 캘린더 화면에서 호출해도 같은 값이 온다. */
  @Operation(summary = "구간 내 공휴일 날짜 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"startDate": "2027-01-01", "endDate": "2027-01-31", "holidays": ["2027-01-01"]}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_INPUT — startDate가 endDate보다 뒤",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
                  """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
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
