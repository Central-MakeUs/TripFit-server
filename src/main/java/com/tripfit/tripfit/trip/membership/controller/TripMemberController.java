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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
// tripId 멤버십은 @TripMemberOnly → TripAuthorizationInterceptor
public class TripMemberController {

  private final TripService tripService;

  public TripMemberController(TripService tripService) {
    this.tripService = tripService;
  }

  /**
   * 여행방 참여자 목록을 조회한다. 이 방에서 ACTIVE인 멤버만 호출할 수 있고, SCHEDULE_PENDING이면 403이다. 동명이인은 표시명에
   * {@code (2)}처럼 번호가 붙는다.
   */
  @TripMemberOnly
  @Operation(summary = "참여자 목록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"memberCount": 6, "activeMemberCount": 3, "memberFillRate": 0.5, "members": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "홍길동", "role": "OWNER", "memberStatus": "ACTIVE", "pinned": true}]}}
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
          description = "TRIP_ACCESS_DENIED — 비참여자 · SCHEDULE_ACTIVATION_REQUIRED — 이 방 일정 확인 미완료(SCHEDULE_PENDING)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "SCHEDULE_ACTIVATION_REQUIRED", "message": "이 여행방 일정 확인을 완료해야 입장할 수 있습니다."}
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
  @GetMapping
  ResponseEntity<SuccessResponse<TripMembersResponse>> listMembers(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.listMembers(tripId, userId)));
  }

  /**
   * 희망 기간(startRange~endRange) 동안 멤버 전원의 정기+개별을 합친 일정을 조회한다. 조율 중(ONGOING)인 방은 실시간 일정을, 확정·종료된 방은
   * 당시 스냅샷(읽기 전용)을 반환한다.
   */
  @TripMemberOnly
  @Operation(summary = "멤버 정기+개별 합친 일정 달력")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"startDate": "2026-08-01", "endDate": "2026-08-31", "readOnly": false, "members": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "홍길동", "role": "OWNER", "memberStatus": "ACTIVE", "days": [{"date": "2026-08-03", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false}]}]}}
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
  @GetMapping("/schedule-calendar")
  ResponseEntity<SuccessResponse<MemberScheduleCalendarResponse>> getScheduleCalendar(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        SuccessResponse.of(tripService.getMemberScheduleCalendar(tripId, userId)));
  }

  /**
   * 방장이 참여자(MEMBER)를 내보낸다. 여행방이 ONGOING이어야 하고 대상은 방장일 수 없다. 대상을 soft delete한 뒤 갱신된 멤버 목록을 반환하며, 추천
   * 캐시는 건드리지 않는다.
   */
  @TripOwnerOnly
  @Operation(summary = "참여자 내보내기")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "내보내기 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"memberCount": 6, "activeMemberCount": 2, "memberFillRate": 0.3333333333333333, "members": [{"userId": "550e8400-e29b-41d4-a716-446655440000", "displayName": "홍길동", "role": "OWNER", "memberStatus": "ACTIVE", "pinned": true}]}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "CANNOT_REMOVE_OWNER — 방장은 내보낼 수 없음",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "CANNOT_REMOVE_OWNER", "message": "방장은 내보낼 수 없습니다."}
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
          description = "TRIP_NOT_FOUND — 여행방 없음 · TRIP_MEMBER_NOT_FOUND — 대상 참여자 없음·이미 내보냄",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_MEMBER_NOT_FOUND", "message": "여행방 참여자를 찾을 수 없습니다."}
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
  @DeleteMapping("/{userId}")
  ResponseEntity<SuccessResponse<TripMembersResponse>> removeMember(
      @PathVariable UUID tripId,
      @PathVariable UUID userId,
      @AuthorizedUser UUID ownerId) {
    return ResponseEntity.ok(SuccessResponse.of(tripService.removeMember(tripId, ownerId, userId)));
  }

  // 이 방 일정 확인(ACTIVE) 여부와 무관하게 나갈 수 있어야 하므로 @TripMemberOnly 미부착 — 서비스에서 직접 멤버십 검증
  /**
   * 참여자(MEMBER)가 스스로 여행방에서 나간다. 방장은 사용할 수 없다(여행방 삭제를 대신 써야 함). 방 상태(ONGOING/CONFIRMED/EXPIRED)와
   * 무관하게 항상 허용되고, 나간 뒤 같은 초대 코드로 다시 참여할 수 있다.
   */
  @Operation(summary = "여행방 나가기")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "나가기 성공(No Content)"),
      @ApiResponse(
          responseCode = "400",
          description = "TRIP_OWNER_CANNOT_LEAVE — 방장은 나갈 수 없음(방 삭제 사용)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "TRIP_OWNER_CANNOT_LEAVE", "message": "방장은 여행방을 나갈 수 없습니다. 여행방 삭제를 이용해주세요."}
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
          description = "TRIP_ACCESS_DENIED — 비참여자 또는 이미 나감",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "TRIP_ACCESS_DENIED", "message": "여행방 참여 권한이 없습니다."}
                  """)))
  })
  @DeleteMapping("/me")
  ResponseEntity<Void> leaveTrip(@PathVariable UUID tripId, @AuthorizedUser UUID userId) {
    tripService.leaveTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }
}
