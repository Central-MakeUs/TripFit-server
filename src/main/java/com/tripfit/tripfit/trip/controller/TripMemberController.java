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

@Tag(name = "Trip Members")
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
  @Operation(summary = "참여자 목록", description = "status·role·pinned·응답률 · 동명이인 `홍길동(2)`")
  @GetMapping
  ResponseEntity<ApiResponse<TripMembersResponse>> listMembers(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.listMembers(tripId, userId)));
  }

  @TripMemberOnly
  @Operation(
      summary = "멤버 effective 일정 달력",
      description = "희망 기간(startRange~endRange) 멤버 전원 effective (#17·#37). "
          + "ONGOING=live. CONFIRMED/TERMINATED=snapshot(#38). CANCELED=409 TRIP_CANCELED")
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
      description = "방장만 MEMBER soft delete. ONGOING만. 200+멤버 목록. 추천 캐시 미터치 (#20)")
  @DeleteMapping("/{userId}")
  ResponseEntity<ApiResponse<TripMembersResponse>> removeMember(
      @PathVariable UUID tripId,
      @PathVariable UUID userId,
      @AuthorizedUser UUID ownerId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.removeMember(tripId, ownerId, userId)));
  }
}
