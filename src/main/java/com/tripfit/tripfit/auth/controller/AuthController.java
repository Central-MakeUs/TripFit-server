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
   * [소셜 로그인]
   * 소셜(Apple/Google) 인증으로 로그인하고 JWT 토큰을 발급합니다.
   *
   * <p>■ FE 유의사항
   * <br>- APPLE/GOOGLE 모두 매 로그인 시 새로 발급받은 authorizationCode를 보내야 합니다.
   * <br>- GOOGLE 브라우저 리다이렉트 로그인이면 redirectUri도 실제 값과 일치하게 보내야 합니다.
   *
   * <p>■ BE 처리
   * <br>- Refresh Token은 HttpOnly 쿠키로 응답하며, 클라이언트에서 직접 다루지 않습니다.
   * <br>- GOOGLE은 redirectUri 불일치 시 토큰 교환이 조용히 스킵될 수 있습니다(best-effort).
   */
  @Operation(summary = "소셜 로그인")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "로그인 성공. Set-Cookie로 refreshToken 전달(HttpOnly)",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED (provider가 APPLE인데 authorizationCode 누락)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED (provider가 GOOGLE인데 authorizationCode 누락)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_SOCIAL_TOKEN_EXPIRED (소셜 토큰 만료 )· AUTH_SOCIAL_TOKEN_INVALID (그 외 소셜 토큰 무효)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "503",
          description = "AUTH_SOCIAL_PROVIDER_UNAVAILABLE (소셜 provider API 접근 실패(네트워크)·타임아웃)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
   * [액세스·리프레시 토큰 재발급 (RTR)]
   * Refresh 쿠키를 이용해 Access/Refresh 토큰을 함께 갱신합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 브라우저에 저장된 기존 refresh token 쿠키를 전송해야 합니다.
   *
   * <p>■ BE 처리
   * <br>- 기존 refresh token은 즉시 폐기되고 새 쿠키가 발급됩니다.
   * <br>- 이미 폐기된 쿠키가 제출되면 탈취로 간주해 연관된 로그인 체인 전체를 폐기합니다.
   */
  @Operation(summary = "액세스·리프레시 토큰 재발급 (RTR)")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "재발급 성공. Set-Cookie로 새 refreshToken 전달(HttpOnly)",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_INVALID_REFRESH (refresh 쿠키 없음)·만료 · AUTH_REFRESH_REUSE (이미 폐기된 refresh 재사용(탈취 의심, 재로그인 필요))",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
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
   * [로그아웃]
   * 발급된 Refresh Token을 폐기하여 재발급을 막습니다.
   *
   * <p>■ FE 유의사항
   * <br>- 액세스 토큰은 남은 수명(TTL) 동안 유효할 수 있습니다 (블랙리스트 없음).
   *
   * <p>■ BE 처리
   * <br>- Refresh Token 쿠키를 서버 및 브라우저에서 폐기합니다.
   * <br>- 쿠키가 없거나 만료됐어도 에러 없이 성공(204) 처리됩니다.
   */
  @Operation(summary = "로그아웃")
  @ApiResponses({
      @ApiResponse(responseCode = "204",
          description = "로그아웃 성공(No Content). Set-Cookie로 refreshToken 쿠키 삭제")
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

  /**
   * [현재 사용자 조회]
   * 로그인한 사용자의 요약 정보를 조회합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 화면 렌더링에 필요한 현재 사용자 상태를 반환합니다.
   *
   * <p>■ BE 처리
   * <br>- hasCompletedPreSchedule 값은 사전 신청일 저장 여부에서 동적 계산됩니다.
   */
  @Operation(summary = "현재 사용자 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/me")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = authService.getCurrentUser(userId);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [Apple 계정 변경 알림 수신]
   * Apple Server-to-Server Notification을 수신합니다.
   *
   * <p>■ FE 유의사항
   * <br>- 클라이언트가 아닌 Apple 서버가 직접 호출하는 웹훅입니다.
   *
   * <p>■ BE 처리
   * <br>- Apple 계정 변경 이벤트(연동 해제, 계정 삭제 등)를 수신해 사용자 정보를 동기화합니다.
   * <br>- consent-revoked는 토큰만 폐기, account-delete는 계정 Soft Delete까지 수행합니다.
   */
  @Operation(summary = "Apple 계정 변경 알림 수신")
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "수신·처리 완료. no-op(존재하지 않는 sub·미인식 type)도 포함"),
      @ApiResponse(
          responseCode = "400",
          description = "AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD (payload)·events JSON 형식 오류·필수 필드 누락",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "AUTH_APPLE_NOTIFICATION_ISSUER_INVALID (iss 불일치 )· AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID (aud 불일치 )· AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID (서명 불일치)·만료",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/apple/notifications")
  ResponseEntity<Void> appleNotifications(
      @Valid @RequestBody AppleNotificationRequest request) {
    AppleNotificationEvent event = appleNotificationVerifier.verify(request.payload());
    appleNotificationService.handle(event);
    return ResponseEntity.ok().build();
  }
}
