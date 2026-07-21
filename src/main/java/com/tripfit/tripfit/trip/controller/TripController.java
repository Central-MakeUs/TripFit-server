package com.tripfit.tripfit.trip.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.trip.config.TripMemberOnly;
import com.tripfit.tripfit.trip.config.TripOwnerOnly;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.service.TripService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trip")
@RestController
@RequestMapping("/api/v1/trips")
@SecurityRequirement(name = "bearer-jwt")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @Operation(summary = "여행방 생성",
      description = "방장 OWNER+JOINED + inviteCode. 일정 confirm 후 입장 (#39). BR-USER-001 이름 필수")
  @PostMapping
  ResponseEntity<ApiResponse<CreateTripResponse>> createTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateTripRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(tripService.createTrip(userId, request)));
  }

  @Operation(
      summary = "내 여행방 목록",
      description = "D5 scope=ongoing|all · TripHomeCardResponse · status는 TripStatus(ONGOING|CONFIRMED|ALL)")
  @GetMapping
  ResponseEntity<ApiResponse<TripListResponse>> listTrips(
      @AuthorizedUser UUID userId,
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "ALL") String status,
      @RequestParam(defaultValue = "false") boolean ownerOnly) {
    TripListQuery query = TripListQuery.parse(scope, status, ownerOnly);
    return ResponseEntity.ok(ApiResponse.of(tripService.listMyTrips(userId, query)));
  }

  @TripMemberOnly
  @Operation(summary = "여행방 상세", description = "참여자만 · TripDetailResponse")
  @GetMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripDetailResponse>> getTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.getTrip(tripId, userId)));
  }

  @TripOwnerOnly
  @Operation(summary = "여행방 메타 수정", description = "방장만 · ONGOING만")
  @PatchMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripDetailResponse>> patchTrip(
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

  @Operation(
      summary = "초대 링크로 참여",
      description = "링크 URL의 inviteCode로 멤버 등록(RESPONDED). 이미 RESPONDED면 idempotent 200. JOINED면 confirm 필요")
  @PostMapping("/join")
  ResponseEntity<ApiResponse<TripDetailResponse>> joinTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.joinTrip(userId, request)));
  }

  @Operation(
      summary = "여행방 일정 확인 완료",
      description = "JOINED → RESPONDED. Skip+0행 시 is_all_free. 이미 RESPONDED면 idempotent. 방 입장 전 필수 (#39)")
  @PostMapping("/{tripId}/schedule/confirm")
  ResponseEntity<ApiResponse<TripDetailResponse>> confirmSchedule(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.confirmSchedule(tripId, userId)));
  }

  @TripMemberOnly
  @Operation(summary = "Pin 토글", description = "본인 is_pinned + pinned_at (D5)")
  @PatchMapping("/{tripId}/pin")
  ResponseEntity<ApiResponse<TripDetailResponse>> updatePin(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateTripPinRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.updatePin(tripId, userId, request)));
  }

}
