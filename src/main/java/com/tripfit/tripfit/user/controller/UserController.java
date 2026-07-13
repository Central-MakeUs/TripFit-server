package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.config.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
import com.tripfit.tripfit.user.dto.UpdateOnboardingRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final UserProfileService userProfileService;

  public UserController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  // JWT 사용자의 성·이름을 저장함
  @Operation(summary = "프로필(성·이름) 저장", description = "Authorization: Bearer {accessToken} 필수")
  @PatchMapping("/profile")
  ResponseEntity<ApiResponse<UserSummaryResponse>> updateProfile(
      @AuthorizedUser
      UUID userId,
      @Valid
      @RequestBody
      UpdateProfileRequest request) {
    UserSummaryResponse response = userProfileService.updateProfile(userId, request);
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  // JWT 사용자의 마이페이지에서 성·이름을 수정함
  @Operation(summary = "마이페이지 이름 수정", description = "Authorization: Bearer {accessToken} 필수")
  @PatchMapping("/my-page")
  ResponseEntity<ApiResponse<UserSummaryResponse>> updateMyPage(
      @AuthorizedUser
      UUID userId,
      @Valid
      @RequestBody
      UpdateMyPageRequest request) {
    UserSummaryResponse response = userProfileService.updateMyPage(userId, request);
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  // JWT 사용자의 선택 온보딩 boolean을 갱신함
  @Operation(summary = "온보딩 상태 갱신", description = "전송한 boolean 필드만 partial update. Bearer JWT 필수")
  @PatchMapping("/onboarding")
  ResponseEntity<ApiResponse<UserSummaryResponse>> updateOnboarding(
      @AuthorizedUser
      UUID userId,
      @Valid
      @RequestBody
      UpdateOnboardingRequest request) {
    UserSummaryResponse response = userProfileService.updateOnboarding(userId, request);
    return ResponseEntity.ok(ApiResponse.of(response));
  }
}
