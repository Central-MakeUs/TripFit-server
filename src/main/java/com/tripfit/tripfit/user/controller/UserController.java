package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
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

@Tag(name = "User", description = "프로필·마이페이지 이름")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserProfileService userProfileService;

  public UserController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @Operation(
      summary = "프로필(성·이름) 저장",
      description = """
          목적: 온보딩에서 성·이름을 저장한다.
          호출 시점: 소셜 로그인 직후 프로필 입력 화면.
          전제: 성·이름 모두 필수.
          결과: UserSummary. hasPreSchedule은 일정 row EXISTS 파생(저장 필드 아님).
          주의: 성·이름 미완료면 여행방 생성·참여가 거부된다.
          주의: 성·이름 미완료면 이후 여행방 생성·참여가 PROFILE_NAME_REQUIRED로 거부된다.
          """)
  @PatchMapping("/profile")
  ResponseEntity<ApiResponse<UserSummaryResponse>> updateProfile(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateProfileRequest request) {
    UserSummaryResponse response = userProfileService.updateProfile(userId, request);
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  @Operation(
      summary = "마이페이지 이름 수정",
      description = """
          목적: 마이페이지에서 성·이름만 수정한다.
          호출 시점: 프로필 수정 화면.
          전제: 성·이름 모두 필수.
          결과: UserSummary. hasPreSchedule은 login/me와 동일하게 조회 시 파생.
          """)
  @PatchMapping("/my-page")
  ResponseEntity<ApiResponse<UserSummaryResponse>> updateMyPage(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody UpdateMyPageRequest request) {
    UserSummaryResponse response = userProfileService.updateMyPage(userId, request);
    return ResponseEntity.ok(ApiResponse.of(response));
  }
}
