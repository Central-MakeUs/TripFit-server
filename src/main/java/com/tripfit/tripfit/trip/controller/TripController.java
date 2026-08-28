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
   * [여행방 생성]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 새 여행방을 만들면 호출자는 자동으로 방장으로 등록됩니다. <br>
   * - 생성 직후에는 방 입장이나 초대 공유가 불가능하며 응답에 inviteCode가 없습니다. <br>
   * - 이후 일정 확인 플로우를 거쳐 activate를 완료해야 ACTIVE 상태가 되며, 상세 조회에서 inviteCode를 얻을 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방 정보를 저장하고 생성자를 방장(OWNER) 권한으로 등록합니다. <br>
   * - 생성된 방장의 멤버 상태는 SCHEDULE_PENDING(activate 전)으로 초기화됩니다.
   */
  @Operation(summary = "여행방 생성")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "PROFILE_NAME_REQUIRED (성)·이름 미입력",
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
   * [내 여행방 목록]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 내가 속한 여행방 카드 목록을 조회합니다. <br>
   * - 목록 카드에는 inviteCode가 포함되지 않습니다(초대 공유는 입장 후 상세 화면에서 수행). <br>
   * - SCHEDULE_PENDING 상태인 카드가 섞여 나올 수 있으며, 이 경우 카드를 탭했을 때 방 상세 화면이 아닌 일정 activate 플로우로 라우팅해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - scope=ongoing: endRange가 오늘 이후인 방 목록을 Pin 여부(우선) 및 일정순으로 정렬합니다. <br>
   * - scope=all: Pin과 무관하게 최근 활동순으로 모든 방을 반환합니다. <br>
   * - ownerOnly 파라미터로 본인이 방장인 방만 필터링할 수 있습니다.
   */
  @Operation(summary = "내 여행방 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
   * [여행방 상세]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - ACTIVE 상태(일정 activate/join 완료)인 멤버만 조회할 수 있습니다. SCHEDULE_PENDING 상태면
   * SCHEDULE_ACTIVATION_REQUIRED로 거부됩니다. <br>
   * - 이 API 응답부터 inviteCode가 포함되어 방장이 초대를 공유할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자 권한 및 멤버 상태를 검증 후 여행방의 상세 정보와 멤버 요약 리스트를 반환합니다.
   */
  @TripMemberOnly
  @Operation(summary = "여행방 상세")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED (비참여자 )· SCHEDULE_ACTIVATION_REQUIRED (이 방 일정 확인 미완료)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
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
   * [여행방 메타 수정]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방 이름, 인원, 여행지 등 메타 정보를 수정합니다. <br>
   * - 희망 기간(startRange, endRange)은 이 API로 수정할 수 없습니다. <br>
   * - 방장만 호출 가능하며, 여행방이 ONGOING(조율 중) 상태일 때만 가능합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 요청자가 방장인지, 여행방이 ONGOING 상태인지 검증 후 메타 정보를 갱신합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 메타 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_FORBIDDEN (방장 아님)",
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
  @PatchMapping("/{tripId}")
  ResponseEntity<SuccessResponse<TripDetailResponse>> patchTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody PatchTripRequest request) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.patchTrip(tripId, userId, request)));
  }

  /**
   * [여행방 삭제]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장 전용 기능이며, 방을 삭제 처리합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 여행방 엔티티를 soft delete 처리합니다. <br>
   * - 연관된 멤버(TripMember) 데이터도 함께 연쇄적으로 soft delete됩니다. <br>
   * - 방장인 경우 SCHEDULE_PENDING 상태(일정 확정 전)에서도 즉시 삭제 가능합니다.
   */
  @TripOwnerOnly
  @Operation(summary = "여행방 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공(No Content)"),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_FORBIDDEN (방장 아님)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
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
   * [초대 링크로 참여]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 초대 링크를 연 직후, 일정 확인 화면에 들어가기 전에 가장 먼저 호출합니다. <br>
   * - 이 API만으로는 방 안의 다른 API(상세 조회 등)를 사용할 수 없습니다. 일정 확인을 마친 후 activate API를 호출해야 합니다. <br>
   * - 이미 참여 중인 사용자가 재호출하면 에러 대신 현재 상태(myMemberStatus)를 그대로 반환합니다(idempotent). 이를 기반으로 일정 플로우로 보낼지 방
   * 안으로 보낼지 라우팅하세요.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자를 해당 방의 멤버로 추가하며 초기 상태는 SCHEDULE_PENDING으로 설정합니다. <br>
   * - 방의 정원은 SCHEDULE_PENDING 멤버도 포함하여 계산하며 초과 시 TRIP_MEMBER_FULL 에러를 반환합니다.
   */
  @Operation(summary = "초대 링크로 참여")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "참여 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "PROFILE_NAME_REQUIRED (성)·이름 미입력",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "INVITE_CODE_NOT_FOUND (초대 코드 없음)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_MEMBER_FULL (정원 초과 )· TRIP_ALREADY_CONFIRMED (확정된 방 )· TRIP_EXPIRED (종료된 방)",
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
   * [여행방 멤버십 활성화]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 일정 확인(사전 일정 입력 등)을 끝낸 후 호출하여 여행방 입장을 완료합니다. <br>
   * - 사전 일정 입력(연차·휴일 등)을 한 번도 완료하지 않았다면 403 PRE_SCHEDULE_REQUIRED가 발생합니다. (등록된 일정이 0건인 것은 문제되지 않음)
   * <br>
   * - 이미 활성화된 상태에서 호출해도 문제없이 동일 응답을 반환합니다(idempotent). <br>
   * - 이 호출 이후부터 방 상세 API 등 모든 방 안 기능을 사용할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 호출자의 멤버십 상태를 SCHEDULE_PENDING에서 ACTIVE로 갱신합니다. <br>
   * - 상태 변경 후 방에 속한 전체 멤버의 요약 정보를 포함한 최신 방 상세 데이터를 반환합니다.
   */
  @Operation(summary = "여행방 멤버십 활성화")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "활성화 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED (비참여자 )· PRE_SCHEDULE_REQUIRED (사전 일정 입력 미완료)",
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
   * [Pin 토글]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 홈 목록에서 특정 방을 상단 고정(Pin)하거나 해제합니다. <br>
   * - 해당 방의 멤버이기만 하면 입장(ACTIVE) 전이더라도 호출 가능합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 멤버십 검증 후 사용자의 해당 방 Pin 상태(boolean)를 토글/저장합니다.
   */
  @TripMembershipOnly
  @Operation(summary = "Pin 토글")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Pin 변경 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED (비참여자)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
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
