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
public class TripMemberController {
  private final TripService tripService;

  public TripMemberController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * [참여자 목록]
   * 여행방 참여자 목록을 조회합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 상태가 ACTIVE인 멤버만 호출 가능합니다 (SCHEDULE_PENDING 상태 시 403 에러).
   * <br>- 동명이인 표시명에 '(2)' 등 번호가 자동 부여됩니다.
   *
   * <p>■ BE 처리
   * <br>- 멤버 권한 및 상태 검증 후 조회합니다.
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
   * 희망 기간(startRange~endRange) 동안 멤버 전원의 스케줄이 취합된 달력 데이터를 조회합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 방 상태(ONGOING, CONFIRMED 등)와 무관하게 동일한 포맷의 달력을 제공합니다.
   *
   * <p>■ BE 처리
   * <br>- 조율 중(ONGOING)인 방은 실시간 일정을 합산하여 반환합니다.
   * <br>- 확정(CONFIRMED)되거나 종료(EXPIRED)된 방은 저장된 스냅샷을 반환합니다.
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
   * 방장이 특정 참여자(MEMBER)를 여행방에서 내보냅니다.
   *
   * <p>■ FE 유의사항
   * <br>- 방장 전용 기능이며 ONGOING 상태일 때만 가능합니다. (자기 자신은 내보낼 수 없음)
   *
   * <p>■ BE 처리
   * <br>- 대상 참여자를 Soft Delete 처리합니다.
   * <br>- 기존 추천 결과 캐시는 유지되므로 필요 시 클라이언트에서 추천 재계산을 유도해야 합니다.
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
   * 참여자 스스로 여행방에서 나갑니다.
   *
   * <p>■ FE 유의사항
   * <br>- ACTIVE 멤버 전용 기능입니다 (일정 확인 전이면 403).
   * <br>- 방장은 방 삭제 API를 사용해야 하며, 이 API는 방장 호출 시 에러가 발생합니다.
   *
   * <p>■ BE 처리
   * <br>- 해당 참여자의 데이터를 Soft Delete 처리합니다.
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
