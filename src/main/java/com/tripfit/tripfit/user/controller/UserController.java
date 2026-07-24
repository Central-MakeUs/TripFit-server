package com.tripfit.tripfit.user.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "프로필·마이페이지·탈퇴")
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

  @Operation(
      summary = "프로필(성·이름) 저장",
      description = """
          목적: 온보딩에서 성·이름을 저장한다.

          호출 시점: 소셜 로그인 직후 프로필 입력 화면.

          전제: 성·이름 모두 필수.

          결과: UserSummary. hasPreSchedule은 일정 row EXISTS 파생(저장 필드 아님).

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

  @Operation(
      summary = "회원 탈퇴",
      description = """
          목적: 본인 계정을 탈퇴한다.

          호출 시점: 마이페이지 탈퇴 확인.

          전제: 없음 — 진행 중인 방이 있어도 차단하지 않는다.

          결과: 참여 중인 방은 자동으로 나가기 처리되고, 소유한 방은 자동으로 삭제된다. 개인 일정·구글 캘린더 연동·리프레시 토큰은 즉시 제거되고, 계정은 soft delete되며 이메일·이름·닉네임·프로필 이미지가 제거된다. 204를 반환한다.

          주의: 소유한 방이 있으면 그 방은 다른 참여자에게도 더 이상 보이지 않는다. 액세스 토큰은 자연 만료 전까지 유효할 수 있다.
          """)
  @DeleteMapping("/me")
  ResponseEntity<Void> withdraw(@AuthorizedUser UUID userId) {
    userWithdrawalService.withdraw(userId);
    return ResponseEntity.noContent().build();
  }
}
