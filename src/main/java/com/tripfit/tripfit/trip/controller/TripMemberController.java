package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Trip Members",
    description = """
        참여자 목록·그룹 달력·내보내기. 전제=방 입장(RESPONDED+입장조건).

        JOINED(방장 confirm 전)는 호출 불가(SCHEDULE_CONFIRM_REQUIRED).
        """)
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
  @GetMapping
  ResponseEntity<ApiResponse<TripMembersResponse>> listMembers(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.listMembers(tripId, userId)));
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
  @GetMapping("/schedule-calendar")
  ResponseEntity<ApiResponse<MemberScheduleCalendarResponse>> getScheduleCalendar(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        ApiResponse.of(tripService.getMemberScheduleCalendar(tripId, userId)));
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
  @DeleteMapping("/{userId}")
  ResponseEntity<ApiResponse<TripMembersResponse>> removeMember(
      @PathVariable UUID tripId,
      @PathVariable UUID userId,
      @AuthorizedUser UUID ownerId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.removeMember(tripId, ownerId, userId)));
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
  @DeleteMapping("/me")
  ResponseEntity<Void> leaveTrip(@PathVariable UUID tripId, @AuthorizedUser UUID userId) {
    tripService.leaveTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }
}
