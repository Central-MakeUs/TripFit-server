package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripMembershipOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.membership.dto.TripJoinPreviewResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trip", description = "여행방 생성·목록·상세·참여·일정 확인·Pin")
@RestController
@RequestMapping("/api/v1/trips")
@SecurityRequirement(name = "bearer-jwt")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * 새 여행방을 만들고 방장으로 등록한다. 방장 멤버 상태는 SCHEDULE_PENDING(방장 전용·activate 전)이 되고, 응답에는 inviteCode가 없다 —
   * 생성만으로는 방 입장·초대 공유가 안 되고, 일정 플로우 후 activate로 ACTIVE가 된 뒤 상세 조회에서 inviteCode를 얻는다.
   */
  @Operation(summary = "여행방 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "status": "ONGOING", "myMemberStatus": "SCHEDULE_PENDING"}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "name", "message": "이름은 최대 15자입니다."}]}
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
          responseCode = "403",
          description = "PROFILE_NAME_REQUIRED — 성·이름 미입력",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "PROFILE_NAME_REQUIRED", "message": "성·이름 입력이 필요합니다."}
                  """)))
  })
  @PostMapping
  ResponseEntity<SuccessResponse<CreateTripResponse>> createTrip(
      @Valid @RequestBody CreateTripRequest request,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SuccessResponse.of(tripService.createTrip(userId, request)));
  }

  /**
   * 내가 속한 여행방 카드 목록을 조회한다. scope=ongoing은 endRange≥오늘 기준 Pin 정렬, scope=all은 Pin 없이 최근 활동순이다.
   * SCHEDULE_PENDING(방장 activate 전) 카드가 섞여 나올 수 있는데, 이 카드는 탭했을 때 상세가 아니라 일정 activate 플로우로 라우팅해야 한다.
   * 목록 카드에는 inviteCode가 없다(공유는 입장 후 상세에서).
   */
  @Operation(summary = "내 여행방 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"trips": [{"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "lastActivityAt": "2026-07-20T10:00:00", "pinned": true, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 3, "memberFillRate": 0.5, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}]}}
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
  ResponseEntity<SuccessResponse<TripListResponse>> listTrips(
      @AuthorizedUser UUID userId,
      @Parameter(description = "목록 뷰. ongoing=진행 중 캐러셀, all=전체",
          example = "all") @RequestParam(defaultValue = "all") String scope,
      @Parameter(description = "여행방 상태 필터. ONGOING|CONFIRMED|ALL",
          example = "ALL") @RequestParam(defaultValue = "ALL") String status,
      @Parameter(description = "true면 본인이 방장인 방만") @RequestParam(
          defaultValue = "false") boolean ownerOnly) {
    TripListQuery query = TripListQuery.parse(scope, status, ownerOnly);
    return ResponseEntity.ok(SuccessResponse.of(tripService.listMyTrips(userId, query)));
  }

  /**
   * 여행방 상세 정보를 조회한다. 멤버이면서 ACTIVE(일정 activate/join 완료)이고 방 입장 조건(일정≥1 또는 전부 free)을 충족해야 하며,
   * SCHEDULE_PENDING(방장 activate 전) 상태면 SCHEDULE_ACTIVATION_REQUIRED로 거부된다. 응답에는 inviteCode가
   * 포함된다(방장 초대 공유용) — create 응답에는 없던 값이다.
   */
  @TripMemberOnly
  @Operation(summary = "여행방 상세")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "inviteCode": "AB12CD", "confirmedStartDate": null, "confirmedEndDate": null, "confirmedAttendCount": null, "confirmedVacationMemberCount": null, "confirmedUncertainCount": null, "lastRecommendationMode": null, "lastActivityAt": "2026-07-20T10:00:00", "pinned": false, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 3, "memberFillRate": 0.5, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
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
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED — 비참여자 · SCHEDULE_ACTIVATION_REQUIRED — 이 방 일정 확인 미완료",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_ACCESS_DENIED", "message": "여행방 참여 권한이 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND — 여행방 없음·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_FOUND", "message": "여행방을 찾을 수 없습니다."}
                  """)))
  })
  @GetMapping("/{tripId}")
  ResponseEntity<SuccessResponse<TripDetailResponse>> getTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.getTrip(tripId, userId)));
  }

  /**
   * 방 이름·인원·여행지 등 메타를 수정한다. 방장만 가능하고, 여행방이 ONGOING(조율 중)이어야 하며, 희망 기간(startRange~endRange)은 이 API로
   * 수정할 수 없다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 메타 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "inviteCode": "AB12CD", "confirmedStartDate": null, "confirmedEndDate": null, "confirmedAttendCount": null, "confirmedVacationMemberCount": null, "confirmedUncertainCount": null, "lastRecommendationMode": null, "lastActivityAt": "2026-07-20T10:00:00", "pinned": false, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 3, "memberFillRate": 0.5, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "name", "message": "이름은 최대 15자입니다."}]}
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
          responseCode = "403",
          description = "TRIP_FORBIDDEN — 방장 아님",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_FORBIDDEN", "message": "여행방 방장만 수행할 수 있습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND — 여행방 없음·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_FOUND", "message": "여행방을 찾을 수 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_ONGOING — 조율 중이 아닌 여행방",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_ONGOING", "message": "조율 중인 여행방만 수정·내보내기·일정 확인할 수 있습니다."}
                  """)))
  })
  @PatchMapping("/{tripId}")
  ResponseEntity<SuccessResponse<TripDetailResponse>> patchTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody PatchTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.patchTrip(tripId, userId, request)));
  }

  /**
   * 여행방을 삭제(soft)한다. 방장이면 SCHEDULE_PENDING(activate 전) 상태에서도 삭제할 수 있다. 멤버 row도 함께 연쇄 soft delete된다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 삭제")
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
          responseCode = "403",
          description = "TRIP_FORBIDDEN — 방장 아님",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_FORBIDDEN", "message": "여행방 방장만 수행할 수 있습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND — 여행방 없음·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_FOUND", "message": "여행방을 찾을 수 없습니다."}
                  """)))
  })
  @DeleteMapping("/{tripId}")
  ResponseEntity<Void> deleteTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    tripService.deleteTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 초대코드로 여행방을 미리보기하며 10분 hold를 생성한다. 정원(확정 멤버 수 + 활성 hold 수)이 가득 찼으면 hold 없이 409로 실패한다. 같은 사용자가
   * 재호출하면 기존 hold의 TTL만 10분으로 갱신되고 자리를 중복으로 소비하지 않는다. 이미 ACTIVE 멤버면 hold 없이 동일 정보를 반환하고,
   * SCHEDULE_PENDING 방장이 호출하면 SCHEDULE_ACTIVATION_REQUIRED로 거부된다.
   */
  @Operation(summary = "초대코드 미리보기 + 정원 hold 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "미리보기·hold 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "activeMemberCount": 3, "status": "ONGOING"}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "inviteCode", "message": "초대 코드는 필수입니다."}]}
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
          responseCode = "403",
          description = "PROFILE_NAME_REQUIRED — 성·이름 미입력 · SCHEDULE_ACTIVATION_REQUIRED — 방장이 호출 시도",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "PROFILE_NAME_REQUIRED", "message": "성·이름 입력이 필요합니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "INVITE_CODE_NOT_FOUND — 초대 코드 없음",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVITE_CODE_NOT_FOUND", "message": "초대 코드를 찾을 수 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_MEMBER_FULL — 정원(hold 포함) 초과 · TRIP_ALREADY_CONFIRMED — 확정된 방 · TRIP_EXPIRED — 종료된 방",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_MEMBER_FULL", "message": "참여 인원이 가득 찼습니다."}
                  """)))
  })
  @PostMapping("/join/hold")
  ResponseEntity<SuccessResponse<TripJoinPreviewResponse>> previewAndHold(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.previewAndHold(userId, request)));
  }

  /**
   * 참여 플로우 첫 화면(정기 일정 입력)에서 뒤로가기로 플로우를 이탈할 때 hold를 즉시 반환한다. 개별 일정 화면에서 정기 일정 화면으로 되돌아가는 것처럼 플로우 안에서
   * 이동하는 뒤로가기는 호출 대상이 아니다(hold 유지). 이미 만료됐거나 join으로 소비된 hold를 호출해도 안전하다(idempotent) — 항상 204.
   */
  @Operation(summary = "정원 hold 해제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "해제 성공(존재하지 않던 hold 포함)"),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @DeleteMapping("/{tripId}/join/hold")
  ResponseEntity<Void> releaseJoinHold(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    tripService.releaseJoinHold(tripId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 초대 코드로 여행방에 참여한다. 멤버는 SCHEDULE_PENDING 없이 join 한 번으로 바로 ACTIVE가 된다(방장의 create 직후
   * SCHEDULE_PENDING은 이 API가 아니라 activate로 처리). 이미 ACTIVE인 멤버가 다시 호출하면 변경 없이 동일 응답을
   * 반환한다(idempotent). 직전에 정원 hold를 발급받았다면 그 hold를 소비해 확정하고, hold가 없거나 만료됐다면 그 시점의 정원(확정 멤버 수 + 다른
   * 사용자의 활성 hold 수)을 다시 확인한다.
   */
  @Operation(summary = "초대 링크로 참여")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "참여 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "inviteCode": "AB12CD", "confirmedStartDate": null, "confirmedEndDate": null, "confirmedAttendCount": null, "confirmedVacationMemberCount": null, "confirmedUncertainCount": null, "lastRecommendationMode": null, "lastActivityAt": "2026-07-20T10:00:00", "pinned": false, "myRole": "MEMBER", "myMemberStatus": "ACTIVE", "activeMemberCount": 3, "memberFillRate": 0.5, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "inviteCode", "message": "초대 코드는 필수입니다."}]}
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
          responseCode = "403",
          description = "PROFILE_NAME_REQUIRED — 성·이름 미입력 · SCHEDULE_ACTIVATION_REQUIRED — 방장이 join으로 우회 시도",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "PROFILE_NAME_REQUIRED", "message": "성·이름 입력이 필요합니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "INVITE_CODE_NOT_FOUND — 초대 코드 없음",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVITE_CODE_NOT_FOUND", "message": "초대 코드를 찾을 수 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_MEMBER_FULL — 정원 초과 · TRIP_ALREADY_CONFIRMED — 확정된 방 · TRIP_EXPIRED — 종료된 방",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_MEMBER_FULL", "message": "참여 인원이 가득 찼습니다."}
                  """)))
  })
  @PostMapping("/join")
  ResponseEntity<SuccessResponse<TripDetailResponse>> joinTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.joinTrip(userId, request)));
  }

  /**
   * 방장의 일정 확인을 끝내 여행방 입장·초대 공유를 가능하게 한다. 본인이 방장이고 myMemberStatus가 SCHEDULE_PENDING(create
   * 직후·activate 전)일 때만 의미가 있다 — 멤버는 이 API를 쓰지 않고 join으로 바로 ACTIVE가 된다. 이미 ACTIVE면 상태 변경 없이 동일
   * 응답(idempotent)이고, 방 안 API는 이 호출 이후에만 쓸 수 있다.
   */
  @Operation(summary = "여행방 멤버십 활성화")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "활성화 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "inviteCode": "AB12CD", "confirmedStartDate": null, "confirmedEndDate": null, "confirmedAttendCount": null, "confirmedVacationMemberCount": null, "confirmedUncertainCount": null, "lastRecommendationMode": null, "lastActivityAt": "2026-07-20T10:00:00", "pinned": false, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 1, "memberFillRate": 0.16666666666666666, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
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
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED — 비참여자",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_ACCESS_DENIED", "message": "여행방 참여 권한이 없습니다."}
                  """)))
  })
  @PostMapping("/{tripId}/activate")
  ResponseEntity<SuccessResponse<TripDetailResponse>> activateMembership(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.activateMembership(tripId, userId)));
  }

  /** 홈 목록에서 이 방을 고정(Pin)하거나 해제한다. 멤버면 되고(SCHEDULE_PENDING 방장 포함), 방 입장(ACTIVE + 입장 조건)까지는 필요 없다. */
  @TripMembershipOnly
  @Operation(summary = "Pin 토글")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Pin 변경 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "ONGOING", "inviteCode": "AB12CD", "confirmedStartDate": null, "confirmedEndDate": null, "confirmedAttendCount": null, "confirmedVacationMemberCount": null, "confirmedUncertainCount": null, "lastRecommendationMode": null, "lastActivityAt": "2026-07-20T10:00:00", "pinned": true, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 3, "memberFillRate": 0.5, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
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
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED — 비참여자",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_ACCESS_DENIED", "message": "여행방 참여 권한이 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND — 여행방 없음·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_FOUND", "message": "여행방을 찾을 수 없습니다."}
                  """)))
  })
  @PatchMapping("/{tripId}/pin")
  ResponseEntity<SuccessResponse<TripDetailResponse>> updatePin(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateTripPinRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.updatePin(tripId, userId, request)));
  }
}
