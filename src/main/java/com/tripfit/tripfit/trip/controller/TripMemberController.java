package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.config.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse;
import com.tripfit.tripfit.trip.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

  private final ScheduleService scheduleService;

  public TripMemberController(TripService tripService, ScheduleService scheduleService) {
    this.tripService = tripService;
    this.scheduleService = scheduleService;
  }

  @TripMemberOnly
  @Operation(summary = "참여자 목록", description = "status·role·pinned·응답률 · 동명이인 `홍길동(2)`")
  @GetMapping
  ResponseEntity<ApiResponse<TripMembersResponse>> listMembers(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.listMembers(tripId, userId)));
  }

  @Hidden // #22 schedule-participation-onboarding [미定]
  @TripMemberOnly
  @Operation(
      summary = "멤버 effective 일정 달력",
      description = "trip 기간 멤버 전원 effective (#17 resolve). wave 2 권장 API")
  @GetMapping("/schedule-calendar")
  ResponseEntity<ApiResponse<MemberScheduleCalendarResponse>> getScheduleCalendar(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        ApiResponse.of(tripService.getMemberScheduleCalendar(tripId, userId)));
  }

  @TripMemberOnly
  @Operation(
      summary = "멤버 개인 일정 요약 (deprecated)",
      description = "personal-only. wave 2 신규 클라이언트는 schedule-calendar 사용. 제거 일정 미정",
      deprecated = true)
  @GetMapping("/personal-summary")
  ResponseEntity<ApiResponse<MemberPersonalSummaryResponse>> getPersonalSummary(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getMemberPersonalSummary(tripId, userId)));
  }
}
