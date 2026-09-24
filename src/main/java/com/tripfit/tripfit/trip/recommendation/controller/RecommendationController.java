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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
public class RecommendationController {
  private final TripService tripService;

  public RecommendationController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * [추천 TOP3 재계산] 선택한 모드로 추천 후보 TOP3를 계산하여 저장합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - "다시 추천받기" 시 같은/다른 모드로 재호출할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 기존 추천 후보는 Soft/Hard Delete 처리되고 새 TOP3로 교체됩니다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 TOP3 재계산")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "계산 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패(INVALID_INPUT). mode가 enum 밖이거나 여행 일수 미정",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_ONGOING (조율 중이 아닌 여행방)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
   * [추천 TOP3 카드 목록] 현재 저장된 추천 후보 TOP3 카드를 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - 카드에는 통계 요약만 포함되며, 참여자별 상세는 '추천 근거 상세' API를 호출해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방의 현재 추천 상태(목록)를 조회하여 반환합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 TOP3 카드 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/recommendations")
  ResponseEntity<SuccessResponse<RecommendationListResponse>> listRecommendations(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.listRecommendations(tripId, userId)));
  }

  /**
   * [추천 근거 상세] 추천 후보 1건의 참여자별 브레이크다운 및 방장의 이전 피드백을 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - 이전에 남긴 피드백이 없으면 feedback 값은 null입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 브레이크다운(연차일수 등) 데이터는 조회 시점에 동적으로 다시 계산하여 반환합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 근거 상세")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음 )· RECOMMENDATION_NOT_FOUND (해당 rank 없음)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/recommendations/{rank}")
  ResponseEntity<SuccessResponse<RecommendationDetailResponse>> getRecommendationDetail(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Parameter(description = "추천 순위 (1~3)") @PathVariable int rank) {
    return ResponseEntity.ok(
        SuccessResponse.of(tripService.getRecommendationDetail(tripId, userId, rank)));
  }

  /**
   * [추천 근거 피드백 저장] 추천 근거 화면에서의 "이 추천이 도움이 되었나요?" 피드백을 저장합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - status=NOT_HELPFUL이면 reason이 필수, reason=OTHER면 reasonDetail 필수입니다. <br>
   * - 같은 후보에 대해 재호출 시 값이 덮어씌워집니다 (Upsert).
   *
   * <p>
   * ■ BE 처리 <br>
   * - 해당 피드백을 Upsert 방식으로 저장하며 재추천 시에도 분석용으로 보존합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "추천 근거 피드백 저장")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "저장 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_RECOMMENDATION_FEEDBACK (사유 누락)·불완전",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음 )· RECOMMENDATION_NOT_FOUND (해당 rank 없음)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/recommendations/{rank}/feedback")
  ResponseEntity<Void> saveRecommendationFeedback(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Parameter(description = "추천 순위 (1~3)") @PathVariable int rank,
      @Valid @RequestBody SaveRecommendationFeedbackRequest request) {
    tripService.saveRecommendationFeedback(tripId, userId, rank, request);
    return ResponseEntity.noContent().build();
  }

  /**
   * [일정 확정] 추천 후보 또는 직접 입력한 날짜로 여행 일정을 최종 확정합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - recommendationRank 또는 (startDate + endDate) 중 하나만 입력해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방 상태를 ONGOING에서 CONFIRMED로 변경합니다. <br>
   * - 확정 시점의 통계를 저장하고 동일 트랜잭션에서 멤버 일정 스냅샷을 고정합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "일정 확정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "확정 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_CONFIRM_REQUEST · CONFIRM_DURATION_MISMATCH · INVALID_INPUT",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음 )· RECOMMENDATION_NOT_FOUND (존재하지 않는 rank)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_ONGOING (조율 중이 아닌 여행방(이미 CONFIRMED면 확정 취소 후 재시도))",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
   * [확정 취소] 확정된 일정을 취소하고 다시 조율 중(ONGOING) 상태로 되돌립니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. <br>
   * - reason 필드 필수, reason=OTHER인 경우 reasonDetail 필수입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방 상태를 CONFIRMED에서 ONGOING으로 변경합니다. <br>
   * - 확정 관련 필드를 초기화하며, 기존 추천 TOP3와 멤버 일정 스냅샷을 삭제합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "확정 취소")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "취소 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "INVALID_UNCONFIRM_REASON",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_CONFIRMED (확정된 방이 아님)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
