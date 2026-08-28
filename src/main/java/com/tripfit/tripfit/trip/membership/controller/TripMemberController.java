package com.tripfit.tripfit.trip.membership.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trip Members", description = "여행방 참여자 목록·그룹 달력·내보내기")
@RestController
@RequestMapping("/api/v1/trips/{tripId}/members")
@SecurityRequirement(name = "bearer-jwt")
public class TripMemberController {
  private final TripService tripService;

  public TripMemberController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * [참여자 목록]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 여행방 참여자 목록을 조회합니다. <br>
   * - 해당 방에서 상태가 ACTIVE인 멤버만 호출할 수 있으며, SCHEDULE_PENDING 상태인 경우 403 에러가 반환됩니다. <br>
   * - 동명이인이 있을 경우 표시명에 {@code (2)}처럼 번호가 붙어 반환됩니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 멤버 권한 및 상태 검증 후 참여자 리스트(TripMember)를 반환합니다.
   */
  @TripMemberOnly
  @Operation(summary = "참여자 목록")
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
          description = "TRIP_ACCESS_DENIED (비참여자 )· SCHEDULE_ACTIVATION_REQUIRED (이 방 일정 확인 미완료(SCHEDULE_PENDING))",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "TRIP_NOT_FOUND (여행방 없음)·soft deleted",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  ResponseEntity<SuccessResponse<TripMembersResponse>> listMembers(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.listMembers(tripId, userId)));
  }

  /**
   * [멤버 정기+개별 합친 일정 달력]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 여행방의 희망 기간(startRange~endRange) 동안 멤버 전원의 스케줄이 취합된 달력 데이터를 조회합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 조율 중(ONGOING)인 방은 실시간 일정을 합산하여 반환합니다. <br>
   * - 이미 확정(CONFIRMED)되거나 종료(EXPIRED)된 방은 당시 저장된 스냅샷(읽기 전용)을 반환합니다.
   */
  @TripMemberOnly
  @Operation(summary = "멤버 정기+개별 합친 일정 달력")
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
  @GetMapping("/schedule-calendar")
  ResponseEntity<SuccessResponse<MemberScheduleCalendarResponse>> getScheduleCalendar(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        SuccessResponse.of(tripService.getMemberScheduleCalendar(tripId, userId)));
  }

  /**
   * [참여자 내보내기]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 방장이 특정 참여자(MEMBER)를 내보낼 때 호출합니다. <br>
   * - 여행방이 ONGOING 상태일 때만 가능하며, 자기 자신(방장)을 내보낼 수는 없습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 대상 참여자를 soft delete 처리하고 갱신된 멤버 목록을 반환합니다. <br>
   * - 단, 추천 캐시는 건드리지 않으므로 이후 추천 재계산이 필요할 수 있습니다.
   */
  @TripOwnerOnly
  @Operation(summary = "참여자 내보내기")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "내보내기 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "CANNOT_REMOVE_OWNER (방장은 내보낼 수 없음)",
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
          description = "TRIP_NOT_FOUND (여행방 없음 )· TRIP_MEMBER_NOT_FOUND (대상 참여자 없음)·이미 내보냄",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "TRIP_NOT_ONGOING (조율 중이 아닌 여행방)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{userId}")
  ResponseEntity<SuccessResponse<TripMembersResponse>> removeMember(
      @PathVariable UUID tripId,
      @PathVariable UUID userId,
      @AuthorizedUser UUID ownerId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.removeMember(tripId, ownerId, userId)));
  }

  /**
   * [여행방 나가기]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 참여자(MEMBER)가 스스로 여행방에서 나갑니다. <br>
   * - 이 방에 입장 완료(ACTIVE)한 멤버만 호출할 수 있고, 일정 확인 전(SCHEDULE_PENDING)이면 403이 발생합니다. <br>
   * - 방장은 이 API를 사용할 수 없습니다 (방 삭제를 대신 사용해야 함). <br>
   * - 방 상태(ONGOING/CONFIRMED/EXPIRED)와 무관하게 언제든 나갈 수 있습니다. <br>
   * - 나간 이후에도 같은 초대 코드로 다시 참여할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 해당 참여자의 TripMember 엔티티를 soft delete 처리합니다.
   */
  @TripMemberOnly
  @Operation(summary = "여행방 나가기")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "나가기 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "TRIP_OWNER_CANNOT_LEAVE (방장은 나갈 수 없음(방 삭제 사용))",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "TRIP_ACCESS_DENIED (비참여자 또는 이미 나감 )· SCHEDULE_ACTIVATION_REQUIRED (이 방 일정 확인 미완료(SCHEDULE_PENDING))",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/me")
  ResponseEntity<Void> leaveTrip(@PathVariable UUID tripId, @AuthorizedUser UUID userId) {
    tripService.leaveTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }
}
