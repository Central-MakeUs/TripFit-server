package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.user.dto.OnboardingNameRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "온보딩 이름 등록·마이페이지 프로필 수정·탈퇴")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserProfileService userProfileService;

  private final UserWithdrawalService userWithdrawalService;

  public UserController(
      UserProfileService userProfileService, UserWithdrawalService userWithdrawalService) {
    this.userProfileService = userProfileService;
    this.userWithdrawalService = userWithdrawalService;
  }

  /**
   * [온보딩 성·이름 최초 등록] 온보딩 과정에서 성과 이름을 최초로 등록합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 이 API 호출 전에는 여행방 생성/참여가 PROFILE_NAME_REQUIRED로 제한됩니다. <br>
   * - 이미 등록 완료된 경우 마이페이지 수정 API를 사용해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 성/이름을 저장하여 회원 프로필을 갱신합니다.
   */
  @Operation(summary = "온보딩 성·이름 최초 등록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true),
  })
  @PatchMapping("/onboarding/name")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> registerOnboardingName(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody OnboardingNameRequest request) {
    UserSummaryResponse response = userProfileService.registerOnboardingName(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [마이페이지 프로필 부분 수정] 이미 등록된 성/이름을 수정하거나 알림 수신 여부를 변경합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - firstName, lastName, notificationEnabled 중 최소 1개 필드를 포함해야 합니다. <br>
   * - 생략(null)된 필드는 수정되지 않습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 요청된 필드만 선택적으로 업데이트(PATCH)합니다.
   */
  @Operation(summary = "마이페이지 프로필(성·이름·알림 설정) 부분 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true),
  })
  @PatchMapping("/profile")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> updateProfile(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateProfileRequest request) {
    UserSummaryResponse response = userProfileService.updateProfile(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [회원 탈퇴] 본인 계정을 탈퇴(삭제)합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 클라이언트가 가진 액세스 토큰은 남은 수명(TTL) 동안 유효할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 진행 중인 모든 방에서 자동 나가기 처리되며, 소유한 방은 연쇄 삭제됩니다. <br>
   * - 개별 일정/구글 캘린더/리프레시 토큰은 Hard Delete, 회원 계정은 비식별화 후 Soft Delete 됩니다.
   */
  @Operation(summary = "회원 탈퇴")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "탈퇴 성공(No Content)"),
  })
  @DeleteMapping("/me")
  ResponseEntity<Void> withdraw(@AuthorizedUser UUID userId) {
    userWithdrawalService.withdraw(userId);
    return ResponseEntity.noContent().build();
  }
}
