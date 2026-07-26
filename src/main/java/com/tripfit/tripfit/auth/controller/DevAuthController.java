package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.dto.DevLoginRequest;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.service.DevAuthService;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Tag(name = "Auth (Dev)", description = "local·dev 전용 테스트 로그인 — prod에는 존재하지 않음")
@RestController
@RequestMapping("/api/v1/auth")
@Profile({"local", "dev"})
public class DevAuthController {

  private final DevAuthService devAuthService;

  public DevAuthController(DevAuthService devAuthService) {
    this.devAuthService = devAuthService;
  }

  @Operation(
      summary = "테스트 로그인 (dev 전용)",
      description = """
          목적: 소셜 토큰 없이 테스트 계정으로 access·refresh를 발급한다.

          호출 시점: 프론트 API 연동 테스트 중 Swagger에 바로 토큰을 넣고 싶을 때.

          전제: local·dev 프로필에서만 동작한다. prod에는 이 API 자체가 존재하지 않는다.

          결과: testUserId별 테스트 계정 기준 access·refresh 토큰과 사용자 요약. chaeyeon·soeun·giyeon은 팀원 3인 고정 계정이고(생략 시 chaeyeon), 그 외 값을 주면 그 값 전용 계정이 새로 생성·재사용된다.

          주요 에러: AUTH_WITHDRAWN_ACCOUNT — 해당 testUserId 테스트 계정이 탈퇴 상태
          """,
      security = {})
  @ApiResponses({
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_WITHDRAWN_ACCOUNT — 해당 testUserId 테스트 계정이 탈퇴 상태",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_WITHDRAWN_ACCOUNT", "message": "탈퇴한 계정입니다."}
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
