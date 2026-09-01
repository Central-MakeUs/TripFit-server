package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.user.dto.OnboardingNameRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
   * 온보딩에서 성·이름을 최초로 등록한다. 등록 이후 재수정은 이 API가 아니라 마이페이지 프로필 수정 ({@code PATCH /users/profile})을 사용한다.
   * 성·이름 미완료면 이후 여행방 생성·참여가 PROFILE_NAME_REQUIRED로 거부된다.
   */
  @Operation(summary = "온보딩 성·이름 최초 등록")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "저장 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "firstName": "길동", "lastName": "홍", "nickname": "홍길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "provider": "GOOGLE", "isGoogleCalendarConnected": false, "hasPreSchedule": false, "isAllFree": false, "notificationEnabled": true}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "name", "message": "이름은 필수입니다."}]}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @PatchMapping("/onboarding/name")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> registerOnboardingName(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody OnboardingNameRequest request) {
    UserSummaryResponse response = userProfileService.registerOnboardingName(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * 온보딩에서 이미 등록된 성·이름을 다시 수정하거나 알림 수신 여부를 변경한다. 최초 이름 등록은 이 API가 아니라 온보딩 성·이름
   * 등록({@code PATCH /users/onboarding/name})을 사용한다. firstName·lastName· notificationEnabled 중 최소 1개
   * 필드를 포함해야 하며, 생략된 필드는 미변경으로 처리되고 요청에 포함된 필드만 응답에 반영된다.
   */
  @Operation(summary = "마이페이지 프로필(성·이름·알림 설정) 부분 수정")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "firstName": "길동", "lastName": "홍", "nickname": "홍길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "provider": "GOOGLE", "isGoogleCalendarConnected": false, "hasPreSchedule": false, "isAllFree": false, "notificationEnabled": false}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "최소 1개 필드가 필요합니다."}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @PatchMapping("/profile")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> updateProfile(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateProfileRequest request) {
    UserSummaryResponse response = userProfileService.updateProfile(userId, request);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * 본인 계정을 탈퇴한다. 진행 중인 방이 있어도 차단하지 않는다 — 참여 중인 방은 자동으로 나가기 처리되고, 소유한 방은 자동 삭제돼 다른 참여자에게도 더 이상 보이지
   * 않는다. 개인 일정·구글 캘린더 연동·리프레시 토큰은 즉시 제거되고, 계정은 soft delete되며 이메일·이름·닉네임·프로필 이미지가 제거된다. 지금 사용 중인 액세스
   * 토큰도 이 요청과 동시에 즉시 무효화된다.
   */
  @Operation(summary = "회원 탈퇴")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "탈퇴 성공(No Content)"),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_EXPIRED", "message": "액세스 토큰이 만료되었습니다."}
                  """)))
  })
  @DeleteMapping("/me")
  ResponseEntity<Void> withdraw(@AuthorizedUser UUID userId) {
    // JwtAuthenticationFilter가 이 요청을 통과시킨 시점에 이미 검증된 JwtAuthentication뿐이므로 안전하게 캐스팅
    JwtAuthentication authentication =
        (JwtAuthentication) SecurityContextHolder.getContext().getAuthentication();
    userWithdrawalService.withdraw(userId, authentication.getJti(), authentication.getExpiresAt());
    return ResponseEntity.noContent().build();
  }
}
