package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.config.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.TripSummaryResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.service.TripService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trip")
@RestController
@RequestMapping("/api/v1/trips")
@SecurityRequirement(name = "bearer-jwt")
// JWT 외 tripId 경로 권한은 TripAuthorizationInterceptor가 @TripMemberOnly/@TripOwnerOnly로 처리함
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @Operation(summary = "여행방 생성", description = "방장 OWNER + inviteCode 발급. BR-USER-001 이름 필수")
  @PostMapping
  ResponseEntity<ApiResponse<CreateTripResponse>> createTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateTripRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(tripService.createTrip(userId, request)));
  }

  @Operation(summary = "내 여행방 목록", description = "is_pinned DESC → trip.updatedAt DESC")
  @GetMapping
  ResponseEntity<ApiResponse<TripListResponse>> listTrips(@AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.listMyTrips(userId)));
  }

  @TripMemberOnly
  @Operation(summary = "여행방 상세", description = "참여자만 조회")
  @GetMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripSummaryResponse>> getTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.getTrip(tripId, userId)));
  }

  @TripOwnerOnly
  @Operation(summary = "여행방 메타 수정", description = "방장만 · ONGOING만")
  @PatchMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripSummaryResponse>> patchTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody PatchTripRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.patchTrip(tripId, userId, request)));
  }

  @TripOwnerOnly
  @Operation(summary = "여행방 삭제", description = "방장 soft delete · trip_member 연쇄 soft")
  @DeleteMapping("/{tripId}")
  ResponseEntity<Void> deleteTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    tripService.deleteTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "초대 코드로 참여", description = "이미 참여 시 idempotent 200")
  @PostMapping("/join")
  ResponseEntity<ApiResponse<TripSummaryResponse>> joinTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.joinTrip(userId, request)));
  }

  @TripMemberOnly
  @Operation(summary = "Pin 토글", description = "본인 trip_member.is_pinned")
  @PatchMapping("/{tripId}/pin")
  ResponseEntity<ApiResponse<TripSummaryResponse>> updatePin(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateTripPinRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.updatePin(tripId, userId, request)));
  }

  @Hidden // #22 schedule-participation-onboarding [미定]
  @TripMemberOnly
  @Operation(
      summary = "일정 제출",
      description = "regular_schedule ≥1 → RESPONDED · ONGOING만")
  @PostMapping("/{tripId}/schedule/submit")
  ResponseEntity<ApiResponse<TripSummaryResponse>> submitSchedule(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.submitSchedule(tripId, userId)));
  }
}
