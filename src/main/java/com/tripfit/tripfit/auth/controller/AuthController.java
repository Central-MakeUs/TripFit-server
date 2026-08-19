package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.auth.dto.AppleNotificationRequest;
import com.tripfit.tripfit.auth.dto.LoginRequest;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.oauth.AppleNotificationEvent;
import com.tripfit.tripfit.auth.oauth.AppleNotificationVerifier;
import com.tripfit.tripfit.auth.security.RefreshCookieFactory;
import com.tripfit.tripfit.auth.service.AppleNotificationService;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

  private final AppleNotificationVerifier appleNotificationVerifier;

  private final AppleNotificationService appleNotificationService;

  private final RefreshCookieFactory refreshCookieFactory;

  public AuthController(
      AuthService authService,
      AppleNotificationVerifier appleNotificationVerifier,
      AppleNotificationService appleNotificationService,
      RefreshCookieFactory refreshCookieFactory) {
    this.authService = authService;
    this.appleNotificationVerifier = appleNotificationVerifier;
    this.appleNotificationService = appleNotificationService;
    this.refreshCookieFactory = refreshCookieFactory;
  }

  /**
   * 소셜 토큰으로 로그인하고 access·refresh를 발급한다. provider가 APPLE 또는 GOOGLE이면 각 OAuth 플로우에서 받은
   * authorizationCode도 함께 보내야 한다({@code LoginRequest.authorizationCode} 필드 설명 참고 — GOOGLE은 네이티브 앱이냐
   * 브라우저 리다이렉트냐에 따라 값을 받는 방법이 다름). GOOGLE 브라우저 리다이렉트 로그인이면 {@code redirectUri}도 실제 리다이렉트에 쓴 값과 정확히
   * 일치하게 보내야 한다.
   *
   * <p>
   * APPLE·GOOGLE은 매번(최초·재로그인 모두) authorizationCode를 새로 발급받아 보내야 한다 — 탈퇴 시 provider revoke에 쓸
   * refresh token을 확보하기 위함. GOOGLE은 재로그인 시 credential이 갱신되지 않을 수 있고(정상 동작), redirectUri가 실제 값과 다르면
   * 토큰 교환이 조용히 스킵된다(로그인 자체는 계속 성공, best-effort).
   *
   * <p>
   * refresh token은 응답 바디가 아니라 HttpOnly 쿠키({@code Set-Cookie: refreshToken=...})로 내려간다 — 클라이언트
   * JavaScript가 값을 직접 다루지 않아도 되고(탈취 표면 축소), 이후 refresh·logout 요청 시 브라우저가 자동으로 실어 보낸다.
   */
  @Operation(summary = "소셜 로그인")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "로그인 성공 — Set-Cookie로 refreshToken 전달(HttpOnly)",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"accessToken": "eyJhbG...", "expiresIn": 900, "user": {"id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "firstName": "길동", "lastName": "홍", "nickname": "홍길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "provider": "GOOGLE", "isGoogleCalendarConnected": false, "hasPreSchedule": false, "isAllFree": false, "notificationEnabled": true}}}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [{"field": "token", "message": "필수 값입니다."}]}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED — provider가 APPLE인데 authorizationCode 누락",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED", "message": "Apple 로그인에는 authorizationCode가 필요합니다."}
                      """))),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED — provider가 GOOGLE인데 authorizationCode 누락",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED", "message": "Google 로그인에는 authorizationCode가 필요합니다."}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_SOCIAL_TOKEN_EXPIRED — 소셜 토큰 만료 · AUTH_SOCIAL_TOKEN_INVALID — 그 외 소셜 토큰 무효",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_SOCIAL_TOKEN_EXPIRED", "message": "소셜 로그인 토큰이 만료되었습니다. 다시 로그인해 주세요."}
                      """))),
      @ApiResponse(
          responseCode = "503",
          description = "AUTH_SOCIAL_PROVIDER_UNAVAILABLE — 소셜 provider API 접근 실패(네트워크·타임아웃)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_SOCIAL_PROVIDER_UNAVAILABLE", "message": "소셜 로그인 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."}
                      """)))
  })
  @PostMapping("/login")
  ResponseEntity<SuccessResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    AuthService.LoginResult result =
        authService.login(
            request.provider(),
            request.token(),
            request.authorizationCode(),
            request.redirectUri());
    ResponseCookie cookie = refreshCookieFactory.issue(result.refreshToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(SuccessResponse.of(result.response()));
  }

  /**
   * refresh token 쿠키로 access·refresh를 함께 재발급한다(RTR) — 기존 refresh는 이 호출과 동시에 폐기되고 응답이 Set-Cookie로
   * 내려주는 새 값으로 브라우저가 자동 교체한다. 이미 폐기된(rotate로 소비된) refresh token이 재제출되면 탈취 재사용으로 간주해 같은 로그인 체인 전체를
   * 폐기한다(401 AUTH_REFRESH_REUSE).
   */
  @Operation(summary = "액세스·리프레시 토큰 재발급 (RTR)")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "재발급 성공 — Set-Cookie로 새 refreshToken 전달(HttpOnly)",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"accessToken": "eyJhbG...", "expiresIn": 900}}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_INVALID_REFRESH — refresh 쿠키 없음·만료 · AUTH_REFRESH_REUSE — 이미 폐기된 refresh 재사용(탈취 의심, 재로그인 필요)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(value = """
                  {"code": "AUTH_INVALID_REFRESH", "message": "유효하지 않은 리프레시 토큰입니다."}
                  """)))
  })
  @PostMapping("/refresh")
  ResponseEntity<SuccessResponse<RefreshResponse>> refresh(
      @Parameter(in = ParameterIn.COOKIE,
          description = "login 또는 이전 refresh 응답이 내려준 refresh token 쿠키") @CookieValue(
              value = "refreshToken", required = false) String refreshToken) {
    if (refreshToken == null) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH);
    }
    AuthService.RefreshResult result = authService.refresh(refreshToken);
    ResponseCookie cookie = refreshCookieFactory.issue(result.refreshToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(SuccessResponse.of(result.response()));
  }

  /**
   * refresh token 쿠키를 폐기해 재발급을 막고 브라우저에서도 쿠키를 지운다. 쿠키가 이미 없거나 만료됐어도 조용히 넘어가고 로그아웃 자체는 계속 성공한다. 액세스
   * 토큰은 블랙리스트 없이 자체 만료(TTL)로만 무효화되므로, 로그아웃 후에도 이미 발급된 액세스 토큰은 남은 수명 동안 유효할 수 있다.
   */
  @Operation(summary = "로그아웃")
  @ApiResponses({
      @ApiResponse(responseCode = "204",
          description = "로그아웃 성공(No Content) — Set-Cookie로 refreshToken 쿠키 삭제")
  })
  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @Parameter(in = ParameterIn.COOKIE,
          description = "폐기할 refresh token 쿠키. 없어도 로그아웃은 성공한다") @CookieValue(value = "refreshToken",
              required = false) String refreshToken) {
    authService.logout(refreshToken);
    ResponseCookie cookie = refreshCookieFactory.clear();
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .build();
  }

  /** 로그인 사용자 요약을 조회한다. hasPreSchedule은 일정 row 존재 여부에서 파생된 값, isAllFree는 DB 컬럼이다. */
  @Operation(summary = "현재 사용자 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true,
          content = @Content(
              examples = @ExampleObject(
                  value = """
                      {"data": {"id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "firstName": "길동", "lastName": "홍", "nickname": "홍길동", "profileImageUrl": "https://lh3.googleusercontent.com/a/example", "provider": "GOOGLE", "isGoogleCalendarConnected": false, "hasPreSchedule": false, "isAllFree": false, "notificationEnabled": true}}
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
  @GetMapping("/me")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = authService.getCurrentUser(userId);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * Apple이 push하는 계정 변경 이벤트(연동 해제·계정 삭제 등)를 수신해 user·refresh_token을 동기화한다. TripFit 클라이언트·로그인 흐름과
   * 무관하게 Apple 서버가 직접 호출한다(Apple Developer Console에 Server-to-Server Notification Endpoint로 등록됨).
   *
   * <p>
   * consent-revoked는 refresh_token만 폐기(계정 유지), account-delete는 user soft delete + refresh_token 폐기,
   * email-enabled/disabled는 로그만 남긴다(user.email 미보유). 존재하지 않는 sub·미인식 type도 200(no-op)이다.
   */
  @Operation(summary = "Apple 계정 변경 알림 수신", security = {})
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "수신·처리 완료 — no-op(존재하지 않는 sub·미인식 type)도 포함"),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD — payload·events JSON 형식 오류·필수 필드 누락",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD", "message": "Apple 알림 payload 형식이 올바르지 않습니다."}
                      """))),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_APPLE_NOTIFICATION_ISSUER_INVALID — iss 불일치 · AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID — aud 불일치 · AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID — 서명 불일치·만료",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class),
              examples = @ExampleObject(
                  value = """
                      {"code": "AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID", "message": "Apple 알림 서명 검증에 실패했습니다."}
                      """)))
  })
  @PostMapping("/apple/notifications")
  ResponseEntity<Void> appleNotifications(
      @Valid @RequestBody AppleNotificationRequest request) {
    AppleNotificationEvent event = appleNotificationVerifier.verify(request.payload());
    appleNotificationService.handle(event);
    return ResponseEntity.ok().build();
  }
}
