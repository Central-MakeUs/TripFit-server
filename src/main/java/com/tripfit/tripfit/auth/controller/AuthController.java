package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.config.AuthorizedUser;
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

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  // 소셜 로그인 요청을 받아 액세스 토큰과 리프레시 토큰을 발급함
  @Operation(
      summary = "소셜 로그인",
      description = "Google/Kakao/Apple 토큰 검증 후 TripFit JWT·refresh token 발급")
  @PostMapping("/login")
  ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request.provider(), request.token());
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  // 리프레시 토큰을 검증해 새로운 액세스 토큰을 재발급함
  @Operation(
      summary = "액세스 토큰 재발급",
      description = "유효한 refresh token으로 access JWT만 재발급 (wave 1: refresh row 유지)")
  @PostMapping("/refresh")
  ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @Valid @RequestBody RefreshRequest request) {
    RefreshResponse response = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  // 전달받은 리프레시 토큰을 삭제해 현재 세션을 로그아웃 처리함
  @Operation(summary = "로그아웃", description = "refresh token row 삭제. 204 No Content")
  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // Bearer JWT로 인증된 현재 사용자 프로필을 반환함
  @Operation(summary = "현재 사용자 조회", description = "Authorization: Bearer {accessToken} 필수")
  @GetMapping("/me")
  ResponseEntity<ApiResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = authService.getCurrentUser(userId);
    return ResponseEntity.ok(ApiResponse.of(response));
  }
}
