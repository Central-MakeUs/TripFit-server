package com.tripfit.tripfit.auth.dev.controller;

import com.tripfit.tripfit.auth.dev.service.DevAuthService;
import com.tripfit.tripfit.auth.dto.DevLoginRequest;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth (Dev)", description = "local·dev 전용 테스트 로그인 — dev는 실제 배포 환경이라 배포 서버에서도 호출 가능")
@RestController
@RequestMapping("/api/v1/auth")
@Profile({"local", "dev"})
public class DevAuthController {

  private final DevAuthService devAuthService;

  public DevAuthController(DevAuthService devAuthService) {
    this.devAuthService = devAuthService;
  }

  /**
   * 소셜 토큰 없이 테스트 계정으로 access·refresh를 발급한다. local·dev 프로필에서만 동작하지만, dev는 실제 배포
   * 환경(api.tripfit.online)과 동일하므로 배포 서버에서도 호출된다. testUserId를 생략하면 chaeyeon 계정,
   * {@code chaeyeon}/{@code soeun}/{@code giyeon}은 팀원 3인 고정 계정, 그 외 값은 해당 값 전용 계정이 새로 생성·재사용된다.
   */
  @Operation(summary = "테스트 로그인 (dev 전용)")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "로그인 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"accessToken": "eyJhbG...", "refreshToken": "550e8400-e29b-41d4-a716-446655440000", "expiresIn": 7200, "user": {"id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "firstName": "길동", "lastName": "홍", "nickname": "홍길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "provider": "GOOGLE", "isGoogleCalendarConnected": false, "hasPreSchedule": false, "isAllFree": false, "notificationEnabled": true}}}
                      """)))
  })
  @PostMapping("/dev-login")
  ResponseEntity<SuccessResponse<LoginResponse>> devLogin(
      @Valid @RequestBody(required = false) DevLoginRequest request) {
    String testUserId = request == null ? null : request.testUserId();
    LoginResponse response = devAuthService.devLogin(testUserId);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }
}
