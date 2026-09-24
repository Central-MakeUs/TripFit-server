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

@Tag(name = "Auth", description = "소셜 로그인, 토큰 재발급, 현재 사용자 조회 기능을 제공합니다.")
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
   * [소셜 로그인] 소셜(Apple/Google) 인증으로 로그인하고 JWT 토큰을 발급합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - APPLE/GOOGLE 모두 매 로그인 시 새로 발급받은 authorizationCode를 보내야 합니다. <br>
   * - GOOGLE 브라우저 리다이렉트 로그인이면 redirectUri도 실제 값과 일치하게 보내야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - Refresh Token은 HttpOnly 쿠키로 응답하며, 클라이언트에서 직접 다루지 않습니다. <br>
   * - GOOGLE은 redirectUri 불일치 시 토큰 교환이 조용히 스킵될 수 있습니다(best-effort).
   */
  @Operation(summary = "소셜 로그인")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "로그인이 성공적으로 처리되었습니다. (Set-Cookie를 통해 refreshToken이 HttpOnly로 전달됩니다)",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "소셜 로그인 제공자가 APPLE인데 인증 코드(authorizationCode)가 누락되었습니다(AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "소셜 로그인 제공자가 GOOGLE인데 인증 코드(authorizationCode)가 누락되었습니다(AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "소셜 토큰이 만료되었거나(AUTH_SOCIAL_TOKEN_EXPIRED) 유효하지 않은 토큰입니다(AUTH_SOCIAL_TOKEN_INVALID).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "503",
          description = "네트워크 문제나 타임아웃 등으로 인해 소셜 제공자 API에 접근할 수 없습니다(AUTH_SOCIAL_PROVIDER_UNAVAILABLE).",
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
   * [액세스·리프레시 토큰 재발급 (RTR)] Refresh 쿠키를 이용해 Access/Refresh 토큰을 함께 갱신합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 브라우저에 저장된 기존 refresh token 쿠키를 전송해야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - 기존 refresh token은 즉시 폐기되고 새 쿠키가 발급됩니다. <br>
   * - 이미 폐기된 쿠키가 제출되면 탈취로 간주해 연관된 로그인 체인 전체를 폐기합니다.
   */
  @Operation(summary = "액세스·리프레시 토큰 재발급 (RTR)")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "토큰 재발급이 성공적으로 처리되었습니다. (Set-Cookie를 통해 새 refreshToken이 HttpOnly로 전달됩니다)",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "refresh 쿠키가 없거나 만료되었거나(AUTH_INVALID_REFRESH), 이미 폐기된 refresh 토큰이 재사용되었습니다(AUTH_REFRESH_REUSE). 재로그인이 필요합니다.",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/refresh")
  ResponseEntity<SuccessResponse<RefreshResponse>> refresh(
      @Parameter(in = ParameterIn.COOKIE,
          description = "로그인 또는 이전 재발급 응답에서 받은 refresh token 쿠키 값입니다.") @CookieValue(
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
   * [로그아웃] 발급된 Refresh Token을 폐기하여 재발급을 막습니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 액세스 토큰은 남은 수명(TTL) 동안 유효할 수 있습니다 (블랙리스트 없음).
   *
   * <p>
   * ■ BE 처리 <br>
   * - Refresh Token 쿠키를 서버 및 브라우저에서 폐기합니다. <br>
   * - 쿠키가 없거나 만료됐어도 에러 없이 성공(204) 처리됩니다.
   */
  @Operation(summary = "로그아웃")
  @ApiResponses({
      @ApiResponse(responseCode = "204",
          description = "로그아웃이 성공적으로 완료되었습니다. (No Content, Set-Cookie를 통해 refreshToken 쿠키가 삭제됩니다)")
  })
  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @Parameter(in = ParameterIn.COOKIE,
          description = "폐기할 refresh token 쿠키 값입니다. (제공되지 않아도 로그아웃은 성공 처리됩니다)") @CookieValue(
              value = "refreshToken",
              required = false) String refreshToken) {
    authService.logout(refreshToken);
    ResponseCookie cookie = refreshCookieFactory.clear();
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .build();
  }

  /**
   * [현재 사용자 조회] 로그인한 사용자의 요약 정보를 조회합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 화면 렌더링에 필요한 현재 사용자 상태를 반환합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - hasCompletedPreSchedule 값은 사전 신청일 저장 여부에서 동적 계산됩니다.
   */
  @Operation(summary = "현재 사용자 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          useReturnTypeSchema = true),
  })
  @GetMapping("/me")
  ResponseEntity<SuccessResponse<UserSummaryResponse>> me(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = authService.getCurrentUser(userId);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [Apple 계정 변경 알림 수신] Apple Server-to-Server Notification을 수신합니다.
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 클라이언트가 아닌 Apple 서버가 직접 호출하는 웹훅입니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - Apple 계정 변경 이벤트(연동 해제, 계정 삭제 등)를 수신해 사용자 정보를 동기화합니다. <br>
   * - consent-revoked는 토큰만 폐기, account-delete는 계정 Soft Delete까지 수행합니다.
   */
  @Operation(summary = "Apple 계정 변경 알림 수신")
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "이벤트 수신 및 처리가 완료되었습니다. (존재하지 않는 sub이거나 인식할 수 없는 type인 경우에도 성공 처리됩니다)"),
      @ApiResponse(
          responseCode = "400",
          description = "payload 또는 events의 JSON 형식 오류가 있거나, 필수 필드가 누락되었습니다(AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD).",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "서명 불일치, 만료 등으로 인해 토큰 검증에 실패했습니다(AUTH_APPLE_NOTIFICATION_ISSUER_INVALID, AUDIENCE_INVALID, SIGNATURE_INVALID).",
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
