package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.config.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
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
public class TripMemberController {

  private final ScheduleService scheduleService;

  public TripMemberController(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @Operation(summary = "멤버 개인 일정 요약", description = "trip 기간 내 날짜별 슬롯 + uncertain")
  @GetMapping("/personal-summary")
  ResponseEntity<ApiResponse<MemberPersonalSummaryResponse>> getPersonalSummary(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(
        ApiResponse.of(scheduleService.getMemberPersonalSummary(tripId, userId)));
  }
}
