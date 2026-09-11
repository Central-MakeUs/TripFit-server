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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "User Schedule", description = "본인 정기·개인 일정과 정기+개별을 합친 달력")
@RestController
@RequestMapping("/api/v1/users/schedule")
public class UserScheduleController {

  private final ScheduleService scheduleService;

  public UserScheduleController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  /** 본인 정기 일정 목록을 생성 시각 오름차순으로 조회한다. 오전·오후·저녁 슬롯은 start/end로 계산된 값이다. */
  @Operation(summary = "정기 일정 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"items": [{"id": "550e8400-e29b-41d4-a716-446655440000", "title": "출근", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "09:00:00", "endTime": "18:00:00", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE"}]}}
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
  @GetMapping("/regular")
  ResponseEntity<SuccessResponse<RegularScheduleListResponse>> listRegular(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.listRegular(userId)));
  }

  /**
   * 매주 반복되는 정기 일정을 추가한다. daysOfWeek는 Weekday(MON~SUN) 콤마 CSV이고, 슬롯은 start/end로 계산된다. 첫 정기 일정 생성 시
   * hasPreSchedule이 true가 된다(GET /auth/me 등으로 재조회 필요).
   */
  @Operation(summary = "정기 일정 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"id": "550e8400-e29b-41d4-a716-446655440000", "title": "출근", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "09:00:00", "endTime": "18:00:00", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE"}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "daysOfWeek", "message": "필수 값입니다."}]}
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
  @PostMapping("/regular")
  ResponseEntity<SuccessResponse<RegularScheduleResponse>> createRegular(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateRegularScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SuccessResponse.of(scheduleService.createRegular(userId, request)));
  }

  /** 기존 정기 일정의 제목·요일·시각·연차 설정을 통째로 갱신한다. start/end가 바뀌면 슬롯을 다시 계산한다. */
  @Operation(summary = "정기 일정 전체 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"id": "550e8400-e29b-41d4-a716-446655440000", "title": "출근", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "09:00:00", "endTime": "18:00:00", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE"}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "daysOfWeek", "message": "필수 값입니다."}]}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "REGULAR_SCHEDULE_NOT_FOUND — 없거나 본인 소유가 아님",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "REGULAR_SCHEDULE_NOT_FOUND", "message": "정기 일정을 찾을 수 없습니다."}
                  """)))
  })
  @PatchMapping("/regular/{id}")
  ResponseEntity<SuccessResponse<RegularScheduleResponse>> updateRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRegularScheduleRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.updateRegular(userId, id, request)));
  }

  /** 본인 정기 일정을 삭제한다. 정기·개인 일정이 모두 0건이 되면 hasPreSchedule이 false가 된다(GET /auth/me 재조회). */
  @Operation(summary = "정기 일정 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공(No Content)"),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "REGULAR_SCHEDULE_NOT_FOUND — 없거나 본인 소유가 아님",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "REGULAR_SCHEDULE_NOT_FOUND", "message": "정기 일정을 찾을 수 없습니다."}
                  """)))
  })
  @DeleteMapping("/regular/{id}")
  ResponseEntity<Void> deleteRegular(
      @AuthorizedUser UUID userId,
      @PathVariable UUID id) {
    scheduleService.deleteRegular(userId, id);
    return ResponseEntity.noContent().build();
  }

  /** 본인 연차·반차·공휴일 휴무 설정을 조회한다. 정기 일정과 별개로 사람 1명에게 하나만 존재한다. */
  @Operation(summary = "연차·반차·공휴일 휴무 설정 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"maxVacationDays": 2, "vacationApplyPeriod": "ONE_WEEK_BEFORE", "halfVacationAvailable": false, "holidayRest": true}}
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
  @GetMapping("/vacation-policy")
  ResponseEntity<SuccessResponse<VacationPolicyResponse>> getVacationPolicy(
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.getVacationPolicy(userId)));
  }

  /**
   * 본인 연차·반차·공휴일 휴무 설정을 전체 교체한다. 부분 수정이 아니라 4개 필드를 매번 전부 보내야 하며, 생략된 필드는 기본값(연차 2일·신청 시점 미설정·반차
   * 불가·공휴일 휴무)으로 대체된다. 이 API는 정기 일정 행이 하나도 없어도 저장할 수 있고, 방 입장·전부 가능(isAllFree) 판정에는 영향을 주지 않는다.
   */
  @Operation(summary = "연차·반차·공휴일 휴무 설정 전체 교체")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"maxVacationDays": 2, "vacationApplyPeriod": "ONE_WEEK_BEFORE", "halfVacationAvailable": false, "holidayRest": true}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
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
  @PatchMapping("/vacation-policy")
  ResponseEntity<SuccessResponse<VacationPolicyResponse>> updateVacationPolicy(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateVacationPolicyRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.updateVacationPolicy(userId, request)));
  }

  /**
   * 여러 날짜에 슬롯(오전/오후/저녁) 오버라이드·불확실 여부를 등록·수정한다. items는 최소 1개, 같은 scheduleDate 중복은 불가하다. 각 항목은
   * slots·uncertain을 독립적으로 선택한다 — 슬롯을 안 건드리려면 slots 필드 자체를 생략(정기+구글 계산값을 그대로 따름), 건드리려면 3개 전부 명시해야
   * 한다. 이 API로는 오버라이드가 삭제되지 않는다 — 한 번 반영된 날짜는 계속 유지된다. 첫 저장 시 hasPreSchedule이 true가 된다(GET /auth/me
   * 재조회 필요).
   */
  @Operation(summary = "개인 일정 슬롯 단위 오버라이드 upsert")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"items": [{"id": "550e8400-e29b-41d4-a716-446655440000", "scheduleDate": "2026-08-03", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false}]}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_INPUT — items 비어 있음·scheduleDate 중복·한 항목에 slots·uncertain 둘 다 없음·slots 필드 일부 누락",
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = @Content(
          examples = {
              @ExampleObject(
                  name = "슬롯만 변경",
                  summary = "slots 3개를 명시. uncertain 필드는 생략 → 기존 값 유지(신규 날짜면 false)",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "하루 종일 불가능으로 전체 오버라이드",
                  summary = "3슬롯 모두 IMPOSSIBLE. 정기 일정이 해당 슬롯을 가능으로 계산해도 개별 오버라이드가 항상 이김",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-06", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "불확실 여부만 변경(ON)",
                  summary = "slots 필드 자체를 생략 → 슬롯 오버라이드는 그대로 두고 uncertain만 갱신",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "uncertain": true}]}
                      """),
              @ExampleObject(
                  name = "불확실 여부 해제(OFF)",
                  summary = "slots 생략, uncertain만 false로 되돌림 → 켜져 있던 동안 저장해둔 슬롯 오버라이드가 그대로 복귀",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-06", "uncertain": false}]}
                      """),
              @ExampleObject(
                  name = "슬롯·불확실 동시 변경",
                  summary = "한 항목에 slots와 uncertain을 같이 담아 한 번에 반영",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}, "uncertain": false}]}
                      """),
              @ExampleObject(
                  name = "구글 캘린더 신호를 덮어쓰는 개별 오버라이드",
                  summary = "정기 일정 없는 날짜라 구글 연동 신호로 오후만 불가능하게 계산됐지만, 개별 오버라이드로 하루 종일 가능 선언 — 구글보다 개별이 항상 우선",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-08", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "하루 종일 가능 재선언",
                  summary = "이미 오버라이드된 날짜에 3슬롯 모두 POSSIBLE + uncertain false로 다시 저장 — 이 값 조합이어도 오버라이드가 삭제되는 경로는 없음, 그대로 새 값으로 저장됨",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}, "uncertain": false}]}
                      """),
              @ExampleObject(
                  name = "정기·구글 신호 없는 날짜에 개별 일정 단독 등록",
                  summary = "정기 일정이 하나도 없는 날짜(예: 주말)에도 개별 오버라이드만 단독으로 등록 가능 — 등록 즉시 캘린더 조회에 나타나고 더 이상 생략되지 않음",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-01", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "❌ 같은 날짜를 아이템 두 개로 분리 → 400",
                  summary = "같은 scheduleDate가 배열에 두 번 오면 400(INVALID_INPUT) — slots·uncertain은 한 아이템에 같이 담을 수 있으므로 나눠 보낼 필요가 없음",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "uncertain": true}, {"scheduleDate": "2026-08-03", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "❌ slots·uncertain 둘 다 없음 → 400",
                  summary = "무엇을 바꾸라는 요청인지 알 수 없어 400(INVALID_INPUT)",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03"}]}
                      """),
              @ExampleObject(
                  name = "❌ slots 슬롯 일부 누락 → 400",
                  summary = "slots 필드를 보내는 순간 아침·오후·저녁 3개 전부 필수 — 일부만 보내면 400(INVALID_INPUT)",
                  value = """
                      {"items": [{"scheduleDate": "2026-08-03", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE"}}]}
                      """),
              @ExampleObject(
                  name = "❌ items 빈 배열 → 400",
                  summary = "items가 비어 있으면 400(INVALID_INPUT)",
                  value = """
                      {"items": []}
                      """)
          }))
  @PatchMapping("/personal")
  ResponseEntity<SuccessResponse<PersonalScheduleResponse>> upsertPersonal(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdatePersonalScheduleRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(scheduleService.upsertPersonal(userId, request)));
  }

  /**
   * 본인 정기 일정과 개별 일정을 합쳐 날짜별 가능/불가능 달력을 조회한다. 요청 구간은 오늘부터 오늘+2년−1일 안이어야 하지만, 참여 중인 조율 중(ONGOING)
   * 여행방의 희망 기간 종료일이 그보다 뒤라면 그 날짜까지 상한이 늘어난다. 날짜별 슬롯은 개인 일정이 정기보다 우선하고, 정기가 여럿이면 IMPOSSIBLE이 우선하며, 빈
   * 날은 응답에서 생략된다. 마이페이지 여행 칩용 방 목록은 GET /trips?scope=ongoing을 따로 호출해야 한다.
   */
  @Operation(summary = "정기+개별 합친 일정 달력 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"startDate": "2026-08-01", "endDate": "2026-08-07", "days": [{"date": "2026-08-03", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false}]}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_INPUT — 조회 구간이 허용 윈도우(오늘~오늘+2년−1, 단 ONGOING 여행 희망 기간 종료일이 뒤면 그 날짜까지) 밖",
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
  @GetMapping("/calendar")
  ResponseEntity<SuccessResponse<ScheduleCalendarResponse>> getCalendar(
      @AuthorizedUser UUID userId,
      @Parameter(description = "달력 시작일(포함). 오늘~오늘+2년−1 안",
          example = "2026-07-22") @RequestParam @DateTimeFormat(
              iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @Parameter(description = "달력 종료일(포함). 오늘~오늘+2년−1 안, ONGOING 여행 희망 기간 종료일이 뒤면 그 날짜까지",
          example = "2026-08-31") @RequestParam @DateTimeFormat(
              iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
        SuccessResponse.of(scheduleService.getCalendar(userId, startDate, endDate)));
  }
}
