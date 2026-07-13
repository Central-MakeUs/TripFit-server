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
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Trip", description = "여행방 생성·목록·상세·참여·일정 확인·Pin")
@RestController
@RequestMapping("/api/v1/trips")
@SecurityRequirement(name = "bearer-jwt")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @Operation(
      summary = "여행방 생성",
      description = """
          목적: 새 여행방을 만들고 방장으로 등록한다.
          호출 시점: 여행방 만들기 완료 직후.
          전제: 성·이름 프로필 완료. 이름은 필수(최대 15자).
          결과: 여행방·초대코드. 방장 멤버 상태는 JOINED(일정 확인 전). 방 안 이용은 schedule/confirm 이후.
          주의: 생성만으로는 방 입장이 안 된다. 일정 플로우 후 confirm이 필요하다.
          주요 에러: PROFILE_NAME_REQUIRED — 성·이름 미입력
          """)
  @PostMapping
  ResponseEntity<ApiResponse<CreateTripResponse>> createTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody CreateTripRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(tripService.createTrip(userId, request)));
  }

  @Operation(
      summary = "내 여행방 목록",
      description = """
          목적: 내가 속한 여행방 카드 목록을 조회한다.
          호출 시점: 홈 캐러셀·전체 여행·마이페이지 여행 칩.
          결과: TripHomeCard 목록. scope=ongoing은 endRange≥오늘·Pin 정렬, scope=all은 Pin 없이 최근 활동순.
          주의: status는 여행방 상태 필터(ONGOING|CONFIRMED|ALL). ownerOnly=true면 방장인 방만.
          """)
  @GetMapping
  ResponseEntity<ApiResponse<TripListResponse>> listTrips(
      @AuthorizedUser UUID userId,
      @Parameter(description = "목록 뷰. ongoing=진행 중 캐러셀, all=전체",
          example = "all") @RequestParam(defaultValue = "all") String scope,
      @Parameter(description = "여행방 상태 필터. ONGOING|CONFIRMED|ALL",
          example = "ALL") @RequestParam(defaultValue = "ALL") String status,
      @Parameter(description = "true면 본인이 방장인 방만") @RequestParam(
          defaultValue = "false") boolean ownerOnly) {
    TripListQuery query = TripListQuery.parse(scope, status, ownerOnly);
    return ResponseEntity.ok(ApiResponse.of(tripService.listMyTrips(userId, query)));
  }

  @TripMemberOnly
  @Operation(
      summary = "여행방 상세",
      description = """
          목적: 여행방 상세 정보를 조회한다.
          호출 시점: 방 홈·설정 화면 진입.
          전제: 멤버이며 일정 확인 완료(RESPONDED)이고 방 입장 조건(일정≥1 또는 전부 free)을 충족.
          결과: TripDetailResponse.
          주요 에러: TRIP_ACCESS_DENIED · SCHEDULE_CONFIRM_REQUIRED · SCHEDULE_ENTRY_REQUIRED
          """)
  @GetMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripDetailResponse>> getTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.getTrip(tripId, userId)));
  }

  @TripOwnerOnly
  @Operation(
      summary = "여행방 메타 수정",
      description = """
          목적: 방 이름·인원·여행지 등 메타를 수정한다.
          호출 시점: 방 설정에서 저장.
          전제: 방장. 여행방이 ONGOING(조율 중). 희망 기간(startRange~endRange)은 수정 불가.
          결과: 갱신된 TripDetailResponse.
          주요 에러: TRIP_FORBIDDEN — 방장 아님 · TRIP_NOT_ONGOING — 조율 중이 아님
          """)
  @PatchMapping("/{tripId}")
  ResponseEntity<ApiResponse<TripDetailResponse>> patchTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody PatchTripRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.patchTrip(tripId, userId, request)));
  }

  @TripOwnerOnly
  @Operation(
      summary = "여행방 삭제",
      description = """
          목적: 여행방을 삭제(soft)한다.
          호출 시점: 방장이 방 삭제 확인.
          전제: 방장. JOINED여도 삭제 가능(일정 confirm 전 방장 허용).
          결과: 204 No Content. 멤버 row도 연쇄 soft delete.
          주요 에러: TRIP_FORBIDDEN — 방장 아님
          """)
  @DeleteMapping("/{tripId}")
  ResponseEntity<Void> deleteTrip(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    tripService.deleteTrip(tripId, userId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "초대 링크로 참여",
      description = """
          목적: 초대 코드로 여행방에 참여한다.
          호출 시점: 초대 링크·코드 입력 후 일정 플로우를 마친 다음.
          전제: 성·이름 완료. 입장 조건(일정≥1 또는 전부 free). 방이 ONGOING이고 정원 여유.
          결과: 멤버가 RESPONDED로 등록되고 TripDetail 반환. 이미 RESPONDED면 변경 없이 동일 응답(idempotent).
          주의: 방장 create 직후 JOINED만인 경우는 이 API가 아니라 schedule/confirm을 쓴다.
          주요 에러: INVITE_CODE_NOT_FOUND · TRIP_MEMBER_FULL · PROFILE_NAME_REQUIRED · SCHEDULE_ENTRY_REQUIRED · TRIP_ALREADY_CONFIRMED · TRIP_CANCELED · TRIP_TERMINATED
          """)
  @PostMapping("/join")
  ResponseEntity<ApiResponse<TripDetailResponse>> joinTrip(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody JoinTripRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.joinTrip(userId, request)));
  }

  @Operation(
      summary = "여행방 일정 확인 완료",
      description = """
          목적: 방장의 일정 확인을 끝내고 여행방 입장을 가능하게 한다.
          호출 시점: 여행방 생성 직후, 일정 확인·입력 플로우를 마친 다음.
          전제: 본인이 해당 방 멤버이고, 멤버 상태가 JOINED(일정 확인 미완료)이다.
          결과: 멤버 상태가 RESPONDED로 바뀌고 여행방 상세를 반환한다. 정기·개별 일정이 모두 없으면 isAllFree가 true가 된다.
          주의: 이미 RESPONDED면 상태 변경 없이 동일 응답(idempotent). 방 안 API는 이 호출 이후에만 사용한다.
          주요 에러: SCHEDULE_ENTRY_REQUIRED — 입장 조건(일정≥1 또는 전부 free) 미충족
          """)
  @PostMapping("/{tripId}/schedule/confirm")
  ResponseEntity<ApiResponse<TripDetailResponse>> confirmSchedule(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId) {
    return ResponseEntity.ok(ApiResponse.of(tripService.confirmSchedule(tripId, userId)));
  }

  @TripMemberOnly
  @Operation(
      summary = "Pin 토글",
      description = """
          목적: 홈 목록에서 이 방을 고정(Pin)하거나 해제한다.
          호출 시점: 카드 Pin 버튼.
          전제: 멤버이며 방 입장 가능(RESPONDED + 입장 조건).
          결과: 본인 isPinned·pinnedAt이 반영된 TripDetail.
          """)
  @PatchMapping("/{tripId}/pin")
  ResponseEntity<ApiResponse<TripDetailResponse>> updatePin(
      @PathVariable UUID tripId,
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateTripPinRequest request) {
    return ResponseEntity.ok(ApiResponse.of(tripService.updatePin(tripId, userId, request)));
  }
}
