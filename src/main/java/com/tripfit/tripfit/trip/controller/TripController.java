package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripMembershipOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.TripEntryResponse;
import com.tripfit.tripfit.trip.membership.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
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

@Tag(name = "Trip", description = "여행방 생성, 목록 조회, 상세 조회, 참여, 일정 확인 및 고정(Pin) 기능을 제공합니다.")
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * [여행방 생성] 새 여행방을 생성하고 호출자를 방장(OWNER)으로 등록합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 생성 직후엔 초대 코드가 없으며, 일정 확인(activate)을 마쳐야 ACTIVE 상태가 되어 초대 코드가 발급됩니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방 저장 및 방장 권한 등록을 수행합니다. <br>
   * - 방장의 초기 상태는 SCHEDULE_PENDING으로 설정됩니다.
   */
  @Operation(summary = "여행방 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "403",
          description = "프로필 이름(성, 이름)이 입력되지 않았습니다(PROFILE_NAME_REQUIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  ResponseEntity<SuccessResponse<TripEntryResponse>> createTrip(
      @Valid @RequestBody CreateTripRequest request,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SuccessResponse.of(tripService.createTrip(userId, request)));
  }

  /**
   * [내 여행방 목록] 내가 속한 여행방 카드 목록을 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 목록에는 inviteCode가 포함되지 않습니다 (상세 화면에서 조회). <br>
   * - SCHEDULE_PENDING 상태인 카드를 탭하면 방 상세가 아닌 '일정 activate 플로우'로 라우팅해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - scope=ongoing: 진행 중인 방을 Pin 여부 및 일정순으로 정렬하여 반환합니다. <br>
   * - scope=all: 모든 방을 최근 활동순으로 정렬하여 반환합니다.
   */
  @Operation(summary = "내 여행방 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
  })
  @GetMapping
  ResponseEntity<SuccessResponse<TripListResponse>> listTrips(
      @AuthorizedUser UUID userId,
      @Parameter(description = "목록 뷰. ongoing=진행 중 캐러셀, all=전체") @RequestParam(
          defaultValue = "all") String scope,
      @Parameter(description = "여행방 상태 필터. ONGOING|CONFIRMED|ALL") @RequestParam(
          defaultValue = "ALL") String status,
      @Parameter(description = "true면 본인이 방장인 방만") @RequestParam(
          defaultValue = "false") boolean ownerOnly) {
    TripListQuery query = TripListQuery.parse(scope, status, ownerOnly);
    return ResponseEntity.ok(SuccessResponse.of(tripService.listMyTrips(userId, query)));
  }

  /**
   * [여행방 상세] 여행방의 상세 정보와 멤버 요약 리스트를 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - ACTIVE 상태(일정 activate 완료)인 멤버만 호출 가능합니다 (SCHEDULE_PENDING 시 에러 발생). <br>
   * - 응답에 inviteCode가 포함되어 초대 공유가 가능해집니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자 권한 및 멤버 ACTIVE 상태 검증 후 데이터를 반환합니다.
   */
  @TripMemberOnly
  @Operation(summary = "여행방 상세")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "403",
          description = "비참여자이거나, 해당 방의 사전 일정 입력을 아직 완료하지 않은 상태(TRIP_ACCESS_DENIED, SCHEDULE_ACTIVATION_REQUIRED)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "요청한 여행방을 찾을 수 없거나 이미 삭제된 상태(TRIP_NOT_FOUND)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{tripId}")
  ResponseEntity<SuccessResponse<TripDetailResponse>> getTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.getTrip(tripId, userId)));
  }

  /**
   * [여행방 메타 수정] 방 이름, 인원, 여행지 등 메타 정보를 수정합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장만 호출 가능하며, 여행방이 ONGOING(조율 중) 상태일 때만 가능합니다. <br>
   * - 희망 기간(startRange, endRange)은 이 API로 수정할 수 없습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 권한(방장) 및 상태(ONGOING) 검증 후 정보를 갱신합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 메타 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "404",
          description = "요청한 여행방을 찾을 수 없거나 이미 삭제된 상태(TRIP_NOT_FOUND)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "현재 조율 중(ONGOING)인 여행방이 아닙니다(TRIP_NOT_ONGOING).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/{tripId}")
  ResponseEntity<SuccessResponse<TripDetailResponse>> patchTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody PatchTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.patchTrip(tripId, userId, request)));
  }

  /**
   * [여행방 삭제] 여행방과 연관된 데이터를 삭제(Soft Delete)합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능입니다. SCHEDULE_PENDING 상태에서도 즉시 삭제 가능합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 방장 권한 검증 후 여행방 엔티티와 연관된 멤버 데이터를 연쇄적으로 Soft Delete 처리합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공(No Content)"),
      @ApiResponse(
          responseCode = "404",
          description = "요청한 여행방을 찾을 수 없거나 이미 삭제된 상태(TRIP_NOT_FOUND)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{tripId}")
  ResponseEntity<Void> deleteTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    tripService.deleteTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * [초대 링크로 참여] 초대 링크를 통해 여행방의 멤버로 참여합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 링크 접속 직후, 가장 먼저 호출해야 합니다. <br>
   * - 이 API 완료 후, 일정 확인 플로우를 거쳐 activate를 호출해야 최종 입장(ACTIVE)됩니다. <br>
   * - 기참여자의 재호출 시 에러 없이 현재 상태(myMemberStatus)를 반환하므로, 이에 맞춰 라우팅 분기 처리가 필요합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자를 멤버로 추가하며 초기 상태를 SCHEDULE_PENDING으로 설정합니다. <br>
   * - PENDING 인원 포함 정원 초과 시 에러를 반환합니다.
   */
  @Operation(summary = "초대 링크로 참여")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "참여 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "403",
          description = "프로필 이름(성, 이름)이 입력되지 않았습니다(PROFILE_NAME_REQUIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "INVITE_CODE_NOT_FOUND (초대 코드 없음)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "여행방 정원이 초과되었거나, 이미 확정 혹은 종료된 방입니다(TRIP_MEMBER_FULL, TRIP_ALREADY_CONFIRMED, TRIP_EXPIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/join")
  ResponseEntity<SuccessResponse<TripEntryResponse>> joinTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.joinTrip(userId, request)));
  }

  /**
   * [여행방 멤버십 활성화] 일정 확인을 마친 후 호출하여 최종적으로 방 입장을 완료(ACTIVE)합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 사전 일정 입력을 한 번도 완료하지 않았다면 403 에러가 발생합니다. <br>
   * - 이미 활성화된 상태에서 호출해도 문제없이 동일 응답을 반환합니다(idempotent). <br>
   * - 이 호출 이후부터 방 상세 API 등 방 안의 모든 기능을 사용할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 호출자의 멤버십 상태를 SCHEDULE_PENDING에서 ACTIVE로 갱신합니다.
   */
  @Operation(summary = "여행방 멤버십 활성화")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "활성화 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "403",
          description = "비참여자이거나, 해당 방의 사전 일정 입력을 아직 완료하지 않은 상태(TRIP_ACCESS_DENIED, PRE_SCHEDULE_REQUIRED)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{tripId}/activate")
  ResponseEntity<SuccessResponse<TripDetailResponse>> activateMembership(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.activateMembership(tripId, userId)));
  }

  /**
   * [Pin 토글] 홈 목록에서 특정 여행방을 상단 고정(Pin)하거나 해제합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 멤버십에 속해 있다면, 입장(ACTIVE) 전이더라도 호출 가능합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 멤버십 검증 후 사용자의 해당 방 Pin 상태를 토글합니다.
   */
  @TripMembershipOnly
  @Operation(summary = "Pin 토글")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Pin 변경 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED (비참여자)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "요청한 여행방을 찾을 수 없거나 이미 삭제된 상태(TRIP_NOT_FOUND)입니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/{tripId}/pin")
  ResponseEntity<SuccessResponse<TripDetailResponse>> updatePin(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateTripPinRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.updatePin(tripId, userId, request)));
  }
}
