package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.auth.dto.LoginRequest;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.LogoutRequest;
import com.tripfit.tripfit.auth.dto.RefreshRequest;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "소셜 로그인·토큰·현재 사용자")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(
      summary = "소셜 로그인",
      description = """
          목적: 소셜 토큰으로 로그인하고 access·refresh를 발급한다.

          호출 시점: 앱 최초 로그인·재로그인.

          전제: Google/Kakao/Apple에서 받은 유효한 토큰.

          결과: access·refresh 토큰과 사용자 요약(hasPreSchedule·isAllFree 포함).

          주요 에러: AUTH_INVALID_TOKEN — 소셜 로그인 토큰 무효
          """,
      security = {})
  @PostMapping("/login")
  ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request.provider(), request.token());
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  @Operation(
      summary = "액세스 토큰 재발급",
      description = """
          목적: refresh token으로 access JWT만 다시 발급한다.

          호출 시점: access 만료 직전·401 이후 재시도.

          전제: 아직 폐기되지 않은 유효한 refresh token.

          결과: 새 access JWT. refresh row는 유지된다.

          주요 에러: AUTH_INVALID_REFRESH — refresh 무효·만료
          """,
      security = {})
  @PostMapping("/refresh")
  ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @Valid @RequestBody RefreshRequest request) {
    RefreshResponse response = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  @Operation(
      summary = "로그아웃",
      description = """
          목적: refresh token을 폐기해 재발급을 막는다.

          호출 시점: 사용자가 로그아웃아웃할 때.

          전제: 본인이 보유한 refresh token.

          결과: 204 No Content. access는 만료까지 유효할 수 있다.
          """,
      security = {})
  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @Operation(
      summary = "현재 사용자 조회",
      description = """
          목적: 로그인 사용자 요약을 조회한다.

          호출 시점: 앱 진입·프로필/일정 변경 후 동기화.

          결과: UserSummary. hasPreSchedule은 일정 row 존재 여부(파생), isAllFree는 DB 컬럼.
          """)
  @GetMapping("/me")
  ResponseEntity<ApiResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = authService.getCurrentUser(userId);
    return ResponseEntity.ok(ApiResponse.of(response));
  }
}
