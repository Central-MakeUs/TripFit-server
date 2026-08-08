package com.tripfit.tripfit.trip.recommendation.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.recommendation.dto.ConfirmTripRequest;
import com.tripfit.tripfit.trip.recommendation.dto.GenerateRecommendationsRequest;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.RecommendationListResponse;
import com.tripfit.tripfit.trip.recommendation.dto.SaveRecommendationFeedbackRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.recommendation.dto.UnconfirmTripRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Recommendation",
    description = "추천 4모드·확정·확정취소. 후보·근거·피드백은 방장 전용, 확정 결과는 방장·참여자 공통(GET /trips/{tripId})")
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@SecurityRequirement(name = "bearer-jwt")
public class RecommendationController {

  private final TripService tripService;

  public RecommendationController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * 선택한 모드로 추천 후보 TOP3를 계산·저장한다("다시 추천받기"로 같은/다른 모드 재호출 가능). 기존 추천 후보는 hard DELETE되고 새 TOP3로 교체되며,
   * 여행방의 마지막 추천 모드가 갱신된다. 이 API를 포함해 후보 조회·근거·피드백은 전부 방장만 접근 가능하다 — 참여자는 확정 전까지 아무것도 볼 수 없다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 TOP3 재계산")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "계산 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"mode": "BASIC", "items": [{"rank": 1, "startDate": "2026-08-03", "endDate": "2026-08-06", "attendRate": 80, "partialAttendCount": 1, "uncertainCount": 1, "totalVacationDays": 2.0}]}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패(INVALID_INPUT) — mode가 enum 밖이거나 여행 일수 미정",
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
  @PostMapping("/recommendations")
  ResponseEntity<SuccessResponse<RecommendationListResponse>> generateRecommendations(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody GenerateRecommendationsRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(
            tripService.generateRecommendations(tripId, userId, request.mode())));
  }

  /**
   * 현재 저장된 추천 후보 TOP3 카드를 조회한다(방장 전용, 참여자가 호출하면 403). mode는 아직 추천 전이면 null이고, 카드에는
   * 참석률·부분참여·불확실·연차일수 통계만 있다 — 참여자별 상세는 후보 1건 조회 API에서 확인한다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 TOP3 카드 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"mode": "BASIC", "items": [{"rank": 1, "startDate": "2026-08-03", "endDate": "2026-08-06", "attendRate": 80, "partialAttendCount": 1, "uncertainCount": 1, "totalVacationDays": 2.0}]}}
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
                  """)))
  })
  @GetMapping("/recommendations")
  ResponseEntity<SuccessResponse<RecommendationListResponse>> listRecommendations(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.listRecommendations(tripId, userId)));
  }

  /**
   * 추천 후보 1건의 참여자별 브레이크다운(전체참석/부분참석/불참·불확실 일수·필요 연차일수)과 방장이 남긴 피드백을 조회한다(방장 전용, 참여자가 호출하면 403).
   * 브레이크다운은 저장값이 아니라 조회 시점에 다시 계산한 값이고(카드 목록을 무겁게 만들지 않기 위함), feedback은 이전에 남긴 피드백이 없으면 null이다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 근거 상세")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"rank": 1, "mode": "BASIC", "startDate": "2026-06-12", "endDate": "2026-06-15", "attendRate": 92, "partialAttendCount": 1, "uncertainCount": 1, "totalVacationDays": 2.0, "members": [{"name": "김유정", "attendance": "NON_ATTEND", "uncertainDays": 0, "vacationDaysNeeded": 0.0}, {"name": "최정연", "attendance": "FULL_ATTEND", "uncertainDays": 2, "vacationDaysNeeded": 2.0}], "feedback": null}}
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
          description = "TRIP_NOT_FOUND — 여행방 없음 · RECOMMENDATION_NOT_FOUND — 해당 rank 없음",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "RECOMMENDATION_NOT_FOUND", "message": "추천 후보를 찾을 수 없습니다."}
                  """)))
  })
  @GetMapping("/recommendations/{rank}")
  ResponseEntity<SuccessResponse<RecommendationDetailResponse>> getRecommendationDetail(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Parameter(description = "추천 순위 (1~3)", example = "1") @PathVariable int rank) {
    return ResponseEntity.ok(
        SuccessResponse.of(tripService.getRecommendationDetail(tripId, userId, rank)));
  }

  /**
   * 추천 근거 화면의 "이 추천이 도움이 되었나요?" 응답을 저장한다. 같은 후보를 다시 열어 다른 선택을 하면 값을 덮어쓴다(upsert).
   * status=NOT_HELPFUL이면 reason이 필수이고, reason=OTHER면 reasonDetail도 필수다. 해당 rank의 recommendation이
   * 재추천으로 삭제된 뒤에도 이 피드백 자체는 분석용으로 보존되지만, 다음 조회에는 노출되지 않는다(새 recommendation에 새로 남겨야 함).
   */
  @TripOwnerOnly
  @Operation(summary = "추천 근거 피드백 저장")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "저장 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_RECOMMENDATION_FEEDBACK — 사유 누락·불완전",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVALID_RECOMMENDATION_FEEDBACK", "message": "추천 피드백 사유를 올바르게 입력해주세요."}
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
          description = "TRIP_NOT_FOUND — 여행방 없음 · RECOMMENDATION_NOT_FOUND — 해당 rank 없음",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "RECOMMENDATION_NOT_FOUND", "message": "추천 후보를 찾을 수 없습니다."}
                  """)))
  })
  @PatchMapping("/recommendations/{rank}/feedback")
  ResponseEntity<Void> saveRecommendationFeedback(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Parameter(description = "추천 순위 (1~3)", example = "1") @PathVariable int rank,
      @Valid @RequestBody SaveRecommendationFeedbackRequest request) {
    tripService.saveRecommendationFeedback(tripId, userId, rank, request);
    return ResponseEntity.noContent().build();
  }

  /**
   * 추천 후보 선택 또는 직접 입력한 날짜로 여행 일정을 확정한다. recommendationRank 또는 (startDate+endDate) 중 정확히 하나만 입력해야
   * 하고, 직접 입력이면 일수가 여행 희망 일수(durationDays)와 같아야 한다. status가 ONGOING→CONFIRMED로 바뀌면서 확정 시점
   * 통계(confirmedAttendCount 등)가 저장되고, 멤버 일정도 같은 트랜잭션에서 스냅샷으로 고정된다. 확정 이후에는 방장·참여자 모두 GET
   * /trips/{tripId}에서 같은 확정 정보를 볼 수 있다.
   */
  @TripOwnerOnly
  @Operation(summary = "일정 확정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "확정 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"tripId": "550e8400-e29b-41d4-a716-446655440000", "name": "제주도 여행", "destination": "제주도", "startRange": "2026-08-01", "endRange": "2026-08-31", "durationDays": 4, "durationNights": 3, "memberCount": 6, "status": "CONFIRMED", "inviteCode": "AB12CD", "confirmedStartDate": "2026-08-03", "confirmedEndDate": "2026-08-06", "confirmedAttendCount": 5, "confirmedVacationMemberCount": 1, "confirmedUncertainCount": 0, "lastRecommendationMode": "BASIC", "lastActivityAt": "2026-07-20T10:00:00", "pinned": false, "myRole": "OWNER", "myMemberStatus": "ACTIVE", "activeMemberCount": 6, "memberFillRate": 1.0, "membersPreview": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "role": "OWNER"}], "membersPreviewOverflow": 0}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_CONFIRM_REQUEST · CONFIRM_DURATION_MISMATCH · INVALID_INPUT",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVALID_CONFIRM_REQUEST", "message": "추천 순위 또는 시작·종료일 중 하나만 입력해주세요."}
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
          description = "TRIP_NOT_FOUND — 여행방 없음 · RECOMMENDATION_NOT_FOUND — 존재하지 않는 rank",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_FOUND", "message": "여행방을 찾을 수 없습니다."}
                  """))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_ONGOING — 조율 중이 아닌 여행방(이미 CONFIRMED면 확정 취소 후 재시도)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_ONGOING", "message": "조율 중인 여행방만 수정·내보내기·일정 확인할 수 있습니다."}
                  """)))
  })
  @PostMapping("/confirm")
  ResponseEntity<SuccessResponse<TripDetailResponse>> confirmSchedule(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody ConfirmTripRequest request) {
    return ResponseEntity.ok(
        SuccessResponse.of(tripService.confirmSchedule(tripId, userId, request)));
  }

  /**
   * 확정된 일정을 취소하고 다시 조율 중(ONGOING) 상태로 되돌린다(새 TripStatus 값을 쓰지 않고 기존 ONGOING으로 단순 복귀). reason이 필수이고,
   * reason=OTHER면 reasonDetail도 필수다. status가 CONFIRMED→ONGOING으로 바뀌며 확정 관련 필드가 전부 초기화되고, 기존 추천
   * TOP3는 hard DELETE되어 재추천이 필요해지며, 멤버 일정 스냅샷도 폐기돼 이후 조회는 다시 라이브 데이터를 쓴다.
   */
  @TripOwnerOnly
  @Operation(summary = "확정 취소")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "취소 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_UNCONFIRM_REASON",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "INVALID_UNCONFIRM_REASON", "message": "확정 취소 사유를 올바르게 입력해주세요."}
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
          description = "TRIP_NOT_CONFIRMED — 확정된 방이 아님",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_NOT_CONFIRMED", "message": "확정된 여행방만 확정을 취소할 수 있습니다."}
                  """)))
  })
  @PostMapping("/unconfirm")
  ResponseEntity<Void> unconfirm(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UnconfirmTripRequest request) {
    tripService.unconfirm(tripId, userId, request);
    return ResponseEntity.noContent().build();
  }
}
