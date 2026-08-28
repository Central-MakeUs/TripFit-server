package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.user.dto.OnboardingNameRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
   * [온보딩 성·이름 최초 등록]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 온보딩 과정에서 성과 이름을 최초로 등록합니다. <br>
   * - 이 API 완료 전에는 여행방 생성이나 참여가 PROFILE_NAME_REQUIRED 에러로 거부됩니다. <br>
   * - 등록 완료 후 재수정할 때는 이 API가 아닌 마이페이지 프로필 수정 ({@code PATCH /users/profile}) API를 사용해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 사용자의 성과 이름을 저장하고 회원 프로필 정보를 갱신합니다.
   */
  @Operation(summary = "온보딩 성·이름 최초 등록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/onboarding/name")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> registerOnboardingName(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody OnboardingNameRequest request) {
    UserSummaryResponse response = userProfileService.registerOnboardingName(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [마이페이지 프로필 부분 수정]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 마이페이지에서 이미 등록된 성/이름을 수정하거나 알림 수신 여부를 변경합니다. <br>
   * - firstName, lastName, notificationEnabled 중 최소 1개 필드를 포함해야 합니다. <br>
   * - 생략된 필드는 미변경으로 처리되고, 요청에 포함된 필드만 응답에 반영됩니다. <br>
   * - 온보딩 단계에서의 최초 이름 등록은 이 API가 아닌 온보딩 성·이름 등록({@code PATCH /users/onboarding/name})을 사용하세요.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 요청된 필드만 선택적으로 부분 업데이트(PATCH)합니다.
   */
  @Operation(summary = "마이페이지 프로필(성·이름·알림 설정) 부분 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/profile")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> updateProfile(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateProfileRequest request) {
    UserSummaryResponse response = userProfileService.updateProfile(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [회원 탈퇴]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 본인 계정을 탈퇴(삭제)합니다. <br>
   * - 액세스 토큰은 블랙리스트 없이 자체 만료(TTL)로만 무효화되므로, 현재 클라이언트가 쥐고 있는 액세스 토큰은 남은 수명 동안 유효할 수 있습니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 진행 중인 방이 있어도 차단하지 않고 처리합니다. 참여 중인 방은 자동으로 나가기 처리되고, 소유한 방은 자동 삭제됩니다. <br>
   * - 개별 일정, 구글 캘린더 연동, 리프레시 토큰은 즉시 hard delete됩니다. <br>
   * - 회원 계정 자체는 soft delete되며, 이메일, 이름, 닉네임, 프로필 이미지 등 개인정보는 비식별화(null 처리)됩니다.
   */
  @Operation(summary = "회원 탈퇴")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "탈퇴 성공(No Content)"),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/me")
  ResponseEntity<Void> withdraw(@AuthorizedUser UUID userId) {
    userWithdrawalService.withdraw(userId);
    return ResponseEntity.noContent().build();
  }
}
