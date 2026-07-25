package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
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

  @TripMemberOnly
  @Operation(
      summary = "참여자 목록",
      description = """
          목적: 여행방 참여자 목록을 조회한다.

          호출 시점: 방 멤버 화면.

          전제: 멤버이며 방 입장 가능(RESPONDED + 입장 조건). JOINED면 403.

          결과: 상태·역할·Pin·모집률 등. 동명이인은 표시명에 `(2)`처럼 번호가 붙는다.
          """)
  @ApiResponses({
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
          description = "TRIP_ACCESS_DENIED — 비참여자 · SCHEDULE_CONFIRM_REQUIRED — 이 방 일정 확인 미완료(JOINED) · SCHEDULE_ENTRY_REQUIRED — 입장 조건 미충족",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "SCHEDULE_CONFIRM_REQUIRED", "message": "이 여행방 일정 확인을 완료해야 입장할 수 있습니다."}
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

  @TripMemberOnly
  @Operation(
      summary = "멤버 effective 일정 달력",
      description = """
          목적: 희망 기간(startRange~endRange) 동안 멤버 전원의 effective 일정을 조회한다.

          호출 시점: 방 안 일정 조율·추천 전 달력.

          전제: 멤버이며 방 입장 가능.

          결과: 멤버별 날짜 슬롯. 조율 중(ONGOING)은 실시간 일정, 확정·종료 방은 당시 스냅샷(읽기 전용).

          주요 에러: TRIP_ACCESS_DENIED / SCHEDULE_CONFIRM_REQUIRED
          """)
  @ApiResponses({
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
          description = "TRIP_ACCESS_DENIED — 비참여자 · SCHEDULE_CONFIRM_REQUIRED — 이 방 일정 확인 미완료",
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

  @TripOwnerOnly
  @Operation(
      summary = "참여자 내보내기",
      description = """
          목적: 방장이 참여자(MEMBER)를 내보낸다.

          호출 시점: 멤버 관리에서 내보내기 확인.

          전제: 방장. 여행방이 ONGOING. 대상은 방장이 아닌 멤버.

          결과: 대상 soft delete 후 갱신된 멤버 목록(200).

          주의: 추천 캐시는 건드리지 않는다.

          주요 에러: TRIP_FORBIDDEN · TRIP_NOT_ONGOING · CANNOT_REMOVE_OWNER · TRIP_MEMBER_NOT_FOUND
          """)
  @ApiResponses({
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

  // 방 입장 조건(RESPONDED·canEnterRoom)과 무관하게 나갈 수 있어야 하므로 @TripMemberOnly 미부착 — 서비스에서 직접 멤버십 검증
  @Operation(
      summary = "여행방 나가기",
      description = """
          목적: 참여자(MEMBER)가 스스로 여행방에서 나간다.

          호출 시점: 마이페이지·방 안 메뉴에서 나가기 확인.

          전제: 호출자가 이 방의 활성 멤버(MEMBER)다. 방장은 사용할 수 없다.

          결과: 본인 참여 기록이 soft delete되고 204를 반환한다. 방 상태(ONGOING/CONFIRMED/EXPIRED)와 무관하게 항상 허용된다.

          주의: 같은 초대 코드로 다시 참여할 수 있다. 방장은 이 API 대신 여행방 삭제를 사용해야 한다.

          주요 에러: TRIP_ACCESS_DENIED — 비참여자 또는 이미 나감 · TRIP_OWNER_CANNOT_LEAVE — 방장
          """)
  @ApiResponses({
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
