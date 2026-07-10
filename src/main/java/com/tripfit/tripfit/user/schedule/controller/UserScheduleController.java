package com.tripfit.tripfit.user.schedule.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse.RegularScheduleListResponse;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Schedule", description = "본인 정기·개인 일정과 달력(effective)")
@RestController
@RequestMapping("/api/v1/users/schedule")
public class UserScheduleController {

  private final ScheduleService scheduleService;

  public UserScheduleController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @Operation(
      summary = "정기 일정 목록",
      description = """
          목적: 본인 정기 일정 목록을 조회한다.

          결과: 생성 시각 오름차순. 오전·오후·저녁 슬롯은 start/end로 계산된 값.
          """)
  @GetMapping("/regular")
  ResponseEntity<ApiResponse<RegularScheduleListResponse>> listRegular(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(scheduleService.listRegular(userId)));
  }

  @Operation(
      summary = "정기 일정 생성",
      description = """
          목적: 매주 반복되는 정기 일정을 추가한다.

          호출 시점: 일정 온보딩·마이페이지에서 정기 일정 추가.

          전제: startTime/endTime·요일(daysOfWeek) 필수.

          결과: 저장된 정기 일정. 슬롯은 start/end로 계산된다. daysOfWeek는 Weekday(MON~SUN) 콤마 CSV.

          주의: 첫 정기 일정 생성 시 hasPreSchedule이 true가 된다(GET /auth/me 등으로 재조회).
          """)
  @PostMapping("/regular")
  ResponseEntity<ApiResponse<RegularScheduleResponse>> createRegular(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateRegularScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(scheduleService.createRegular(userId, request)));
  }

  @Operation(
      summary = "정기 일정 전체 수정",
      description = """
          목적: 기존 정기 일정의 제목·요일·시각·연차 설정을 통째로 갱신한다.

          호출 시점: 정기 일정 편집 저장.

          전제: 본인 소유 일정 ID.

          결과: 갱신된 정기 일정. start/end 변경 시 슬롯을 다시 계산한다.

          주요 에러: REGULAR_SCHEDULE_NOT_FOUND — 없거나 본인 소유가 아님
          """)
  @PatchMapping("/regular/{id}")
  ResponseEntity<ApiResponse<RegularScheduleResponse>> updateRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRegularScheduleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.updateRegular(userId, id, request)));
  }

  @Operation(
      summary = "정기 일정 삭제",
      description = """
          목적: 본인 정기 일정을 삭제한다.

          호출 시점: 정기 일정 삭제.

          전제: 본인 소유 일정 ID.

          결과: 204 No Content.

          주의: 정기·개인이 모두 0건이 되면 hasPreSchedule이 false가 된다(GET /auth/me 재조회).

          주요 에러: REGULAR_SCHEDULE_NOT_FOUND — 없거나 본인 소유가 아님
          """)
  @DeleteMapping("/regular/{id}")
  ResponseEntity<Void> deleteRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id) {
    scheduleService.deleteRegular(userId, id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "개인 일정 조회",
      description = """
          목적: 날짜 구간별 개인(예외) 일정을 조회한다.

          호출 시점: 개인 일정 편집 화면 진입.

          전제: startDate·endDate 필수.

          결과: 날짜당 오전·오후·저녁 슬롯과 uncertain 플래그.
          """)
  @GetMapping("/personal")
  ResponseEntity<ApiResponse<PersonalScheduleResponse>> getPersonal(
      @AuthorizedUser UUID userId,
      @Parameter(description = "조회 시작일(포함)", example = "2026-08-01") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "조회 종료일(포함)", example = "2026-08-31") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getPersonal(userId, startDate, endDate)));
  }

  @Operation(
      summary = "개인 일정 bulk upsert",
      description = """
          목적: 여러 날짜의 개인 일정을 한 번에 저장·삭제한다.

          호출 시점: 개인 일정 편집 저장.

          전제: items(upsert)와 deletedDates(삭제) 중 하나 이상 필요. 같은 날짜가 양쪽에 있으면 안 된다.

          결과: 반영 후 해당 구간 개인 일정 목록.

          주의: 첫 저장 시 hasPreSchedule true. 전부 삭제 후 정기·개인 0건이면 false(GET /auth/me 재조회).

          주요 에러: INVALID_INPUT — items·deletedDates 교집합 날짜 또는 둘 다 비어 있음
          """)
  @PatchMapping("/personal")
  ResponseEntity<ApiResponse<PersonalScheduleResponse>> upsertPersonal(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdatePersonalScheduleRequest request) {
    return ResponseEntity.ok(ApiResponse.of(scheduleService.upsertPersonal(userId, request)));
  }

  @Operation(
      summary = "일정 달력(effective) 조회",
      description = """
          목적: 본인 전역 가능/불가능 달력(effective)을 조회한다.

          호출 시점: 마이페이지 달력·일정 확인 화면.

          전제: 요청 구간은 오늘부터 오늘+2년−1일 안이어야 한다.

          결과: 날짜별 effective 슬롯. 개인 일정이 정기보다 우선하고, 정기 복수면 IMPOSSIBLE이 우선. 빈 날은 응답에서 생략.

          주의: 마이페이지 여행 칩용 방 목록은 GET /trips?scope=ongoing을 따로 호출한다.

          주요 에러: INVALID_INPUT — 조회 구간이 허용 윈도우 밖
          """)
  @GetMapping("/calendar")
  ResponseEntity<ApiResponse<ScheduleCalendarResponse>> getCalendar(
      @AuthorizedUser UUID userId,
      @Parameter(description = "달력 시작일(포함). 오늘~오늘+2년−1 안",
          example = "2026-07-22") @RequestParam @DateTimeFormat(
              iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "달력 종료일(포함). 오늘~오늘+2년−1 안",
          example = "2026-08-31") @RequestParam @DateTimeFormat(
              iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getCalendar(userId, startDate, endDate)));
  }
}
