package com.tripfit.tripfit.user.schedule.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse.RegularScheduleListResponse;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdateVacationPolicyRequest;
import com.tripfit.tripfit.user.schedule.dto.VacationPolicyResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "User Schedule", description = "사용자의 정기 및 개별 일정을 관리하고, 병합된 달력을 조회하는 기능을 제공합니다.")
@RestController
@RequestMapping("/api/v1/users/schedule")
public class UserScheduleController {
  private final ScheduleService scheduleService;

  public UserScheduleController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  /**
   * [정기 일정 목록] 본인의 정기 일정 목록을 생성 시각 오름차순으로 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 오전/오후/저녁 슬롯 정보가 포함되어 반환됩니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - DB에 저장된 start/end 시각을 기준으로 슬롯을 계산하여 응답합니다.
   */
  @Operation(summary = "정기 일정 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
  })
  @GetMapping("/regular")
  ResponseEntity<SuccessResponse<RegularScheduleListResponse>> listRegular(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.listRegular(userId)));
  }

  /**
   * [정기 일정 생성] 매주 반복되는 정기 일정을 추가합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - daysOfWeek는 Weekday(MON~SUN)를 콤마로 구분한 CSV 형식으로 전송해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 입력된 start/end 시각을 분석해 슬롯을 계산하고 저장합니다.
   */
  @Operation(summary = "정기 일정 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          useReturnTypeSchema = true),
  })
  @PostMapping("/regular")
  ResponseEntity<SuccessResponse<RegularScheduleResponse>> createRegular(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateRegularScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SuccessResponse.of(scheduleService.createRegular(userId, request)));
  }

  /**
   * [정기 일정 전체 수정] 기존 정기 일정의 제목·요일·시각·연차 설정 등을 통째로 갱신합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 해당 id의 정기 일정 데이터를 덮어씁니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - start/end 시각 변경 시 슬롯 데이터를 재계산하여 업데이트합니다.
   */
  @Operation(summary = "정기 일정 전체 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "404",
          description = "수정할 정기 일정을 찾을 수 없거나 본인 소유의 일정이 아닙니다(REGULAR_SCHEDULE_NOT_FOUND).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/regular/{id}")
  ResponseEntity<SuccessResponse<RegularScheduleResponse>> updateRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRegularScheduleRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.updateRegular(userId, id, request)));
  }

  /**
   * [정기 일정 삭제] 본인의 정기 일정을 한 건 단위로 삭제합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 본인 소유가 아닌 일정의 id를 요청하면 404 에러가 발생합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 해당 id를 가진 정기 일정 데이터를 Hard Delete 합니다.
   */
  @Operation(summary = "정기 일정 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "정기 일정 삭제가 성공적으로 완료되었습니다. (No Content)"),
      @ApiResponse(
          responseCode = "404",
          description = "삭제할 정기 일정을 찾을 수 없거나 본인 소유의 일정이 아닙니다(REGULAR_SCHEDULE_NOT_FOUND).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/regular/{id}")
  ResponseEntity<Void> deleteRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id) {
    scheduleService.deleteRegular(userId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * [정기 일정 전체 삭제] 본인의 정기 일정을 전부 삭제합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 사전 일정 입력 플로우에서 "정기 일정이 있나요? → 없어요"를 선택했을 때 호출합니다. <br>
   * - 정기 일정이 0건이어도 정상(204) 처리됩니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 정기 일정만 일괄 삭제하며, 개별 일정과 연차·휴일 정보는 유지됩니다.
   */
  @Operation(summary = "정기 일정 전체 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204",
          description = "정기 일정 일괄 삭제가 성공적으로 완료되었습니다. 일정이 없는 경우에도 성공 처리됩니다. (No Content)"),
  })
  @DeleteMapping("/regular")
  ResponseEntity<Void> deleteAllRegular(@AuthorizedUser UUID userId) {
    scheduleService.deleteAllRegular(userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * [연차·휴일 정보 조회] 본인의 연차·휴일 정보를 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 한 명당 하나의 데이터만 존재합니다. <br>
   * - advanceLeaveDays가 null이면 사전 일정 입력을 아직 완료하지 않은 대상입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자의 휴일 정책 정보를 DB에서 조회하여 반환합니다.
   */
  @Operation(summary = "연차·휴일 정보 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
  })
  @GetMapping("/vacation-policy")
  ResponseEntity<SuccessResponse<VacationPolicyResponse>> getVacationPolicy(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.getVacationPolicy(userId)));
  }

  /**
   * [연차·휴일 정보 전체 교체] 본인의 연차·휴일 정보를 전체 교체(저장)합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 4개 필드 모두 전송 필수이며 누락 시 400 에러가 발생합니다. <br>
   * - 이 호출을 완료하면 "사전 일정 입력 완료" 상태로 간주되어 이후 방 입장 시 해당 플로우를 건너뜁니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 4개 필드를 DB에 저장(교체)하고 사전 신청일 값을 할당합니다.
   */
  @Operation(summary = "연차·휴일 정보 전체 교체")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "필수 필드가 누락되었거나 값 검증에 실패했습니다(INVALID_INPUT). (필수 4개 필드 중 하나라도 누락 시 발생)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
  })
  @PatchMapping("/vacation-policy")
  ResponseEntity<SuccessResponse<VacationPolicyResponse>> updateVacationPolicy(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateVacationPolicyRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.updateVacationPolicy(userId, request)));
  }

  /**
   * [개별 일정 슬롯 단위 오버라이드 upsert] 특정 날짜들의 슬롯(오전/오후/저녁) 상태 및 불확실(uncertain) 여부를 덮어씁니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - items는 1개 이상 필수이며 scheduleDate는 중복 불가입니다. <br>
   * - slots 필드를 보낼 경우 반드시 3개 슬롯 모두 명시해야 합니다. <br>
   * - 달력 조회 허용 윈도우(오늘 ~ 오늘+2년-1)를 벗어난 날짜 포함 시 400 에러입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 각 날짜별 개별 일정을 Upsert 합니다 (한 번 반영된 기록은 이 API로 삭제 불가).
   */
  @Operation(summary = "개별 일정 슬롯 단위 오버라이드 upsert")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "입력값이 올바르지 않습니다(INVALID_INPUT). (items가 비어있음, scheduleDate 중복, 한 항목에 slots와 uncertain 동시 누락, slots 필드 일부 누락, 또는 scheduleDate가 허용된 윈도우 범위를 벗어남)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
  })
  @PatchMapping("/personal")
  ResponseEntity<SuccessResponse<PersonalScheduleResponse>> upsertPersonal(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdatePersonalScheduleRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.upsertPersonal(userId, request)));
  }

  /**
   * [정기+개별 합친 일정 달력 조회] 본인의 정기 일정과 개별 일정을 병합한 날짜별 가능/불가능 슬롯 달력을 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 스케줄이 비어 있는 날짜는 응답에서 생략됩니다. <br>
   * - 여행방별 달력이 아니므로, 진행 중(ONGOING)인 방의 일정을 위한 조회 시에 사용합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 정기 일정, 개별 일정, (연동 시) 구글 일정을 병합하여 슬롯 값을 계산합니다. <br>
   * - 우선순위: 개별 일정 > 구글/정기 일정 (정기가 여럿이면 불가능 우선).
   */
  @Operation(summary = "정기+개별 합친 일정 달력 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "조회 구간이 허용된 윈도우 범위를 벗어났습니다(INVALID_INPUT). (허용 범위: 오늘부터 최대 2년 후까지. 단, 조율 중인 여행의 희망 종료일이 더 뒤라면 그 날짜까지 허용)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
  })
  @GetMapping("/calendar")
  ResponseEntity<SuccessResponse<ScheduleCalendarResponse>> getCalendar(
      @AuthorizedUser UUID userId,
      @Parameter(description = "달력 시작일(포함). 오늘~오늘+2년−1 안") @RequestParam @DateTimeFormat(
          iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(
          description = "달력 종료일(포함). 오늘~오늘+2년−1 안, ONGOING 여행 희망 기간 종료일이 뒤면 그 날짜까지") @RequestParam @DateTimeFormat(
              iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.getCalendar(userId, startDate, endDate)));
  }
}
