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
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "User Schedule")
@RestController
@RequestMapping("/api/v1/users/schedule")
public class UserScheduleController {

  private final ScheduleService scheduleService;

  public UserScheduleController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @Operation(summary = "정기 일정 목록", description = "생성 시각 오름차순. 슬롯은 start/end로 계산된 값")
  @GetMapping("/regular")
  ResponseEntity<ApiResponse<RegularScheduleListResponse>> listRegular(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(scheduleService.listRegular(userId)));
  }

  @Operation(
      summary = "정기 일정 생성",
      description = "startTime/endTime으로 슬롯 계산 후 저장. daysOfWeek는 Weekday(MON~SUN) 콤마 CSV")
  @PostMapping("/regular")
  ResponseEntity<ApiResponse<RegularScheduleResponse>> createRegular(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateRegularScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(scheduleService.createRegular(userId, request)));
  }

  @Operation(
      summary = "정기 일정 전체 수정",
      description = "title·요일·시각·연차 전체 갱신. start/end 변경 시 슬롯 재계산")
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
      description = "본인 소유만 삭제. 남은 정기 일정이 없으면 isScheduleRegistered=false")
  @DeleteMapping("/regular/{id}")
  ResponseEntity<Void> deleteRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id) {
    scheduleService.deleteRegular(userId, id);
    return ResponseEntity.noContent().build();
  }

  @Hidden // #22 schedule-participation-onboarding [미定]
  @Operation(
      summary = "개인 일정 조회",
      description = "startDate·endDate 필수. 날짜당 슬롯3 + uncertain")
  @GetMapping("/personal")
  ResponseEntity<ApiResponse<PersonalScheduleResponse>> getPersonal(
      @AuthorizedUser UUID userId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getPersonal(userId, startDate, endDate)));
  }

  @Hidden // #22 schedule-participation-onboarding [미定]
  @Operation(
      summary = "개인 일정 bulk upsert",
      description = "날짜별 슬롯·uncertain 생성 또는 갱신 후 요청 기간 목록 반환")
  @PatchMapping("/personal")
  ResponseEntity<ApiResponse<PersonalScheduleResponse>> upsertPersonal(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdatePersonalScheduleRequest request) {
    return ResponseEntity.ok(ApiResponse.of(scheduleService.upsertPersonal(userId, request)));
  }

  @Hidden // #22 schedule-participation-onboarding [미定]
  @Operation(
      summary = "일정 달력(effective) 조회",
      description = "기간 내 날짜별 합친 슬롯. personal 우선(S1), regular 복수는 IMPOSSIBLE 우선(R2=A). "
          + "빈 날은 omit. start~end 최대 730일(약 2년). 정기 미등록 시 403")
  @GetMapping("/calendar")
  ResponseEntity<ApiResponse<ScheduleCalendarResponse>> getCalendar(
      @AuthorizedUser UUID userId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getCalendar(userId, startDate, endDate)));
  }
}
