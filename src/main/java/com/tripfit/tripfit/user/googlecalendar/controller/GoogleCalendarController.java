package com.tripfit.tripfit.user.googlecalendar.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.SuccessResponse;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.googlecalendar.dto.ConnectGoogleCalendarRequest;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Google Calendar", description = "Google Calendar OAuth 연동·해제")
@RestController
@RequestMapping("/api/v1/users/google-calendar")
public class GoogleCalendarController {
  private final GoogleCalendarService googleCalendarService;

  public GoogleCalendarController(GoogleCalendarService googleCalendarService) {
    this.googleCalendarService = googleCalendarService;
  }

  /**
   * [Google Calendar 연동]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 앱/웹에서 Google OAuth 동의 후 받은 authorization code로 구글 캘린더 읽기 권한을 연동합니다. <br>
   * - 브라우저 리다이렉트 방식인 경우 redirectUri도 함께 보내야 합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - authorization code를 검증하여 연동을 완료하고, 연동 직후 freeBusy(스케줄) 정보를 1회 동기화합니다.
   */
  @Operation(summary = "Google Calendar 연동")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "연동 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값 검증 실패 (INVALID_INPUT)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "502",
          description = "GOOGLE_CALENDAR_CONNECT_FAILED (authorization code 교환)·Google API 호출 실패",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  ResponseEntity<SuccessResponse<UserSummaryResponse>> connect(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody ConnectGoogleCalendarRequest request) {
    UserSummaryResponse response =
        googleCalendarService.connect(userId, request.authorizationCode(), request.redirectUri());
    return ResponseEntity.ok(SuccessResponse.of(response));
  }

  /**
   * [Google Calendar 연동 해제]
   *
   * <p>
   * ■ FE 유의사항 <br>
   * - 사용자가 의도적으로 구글 캘린더 연동을 해제할 때 호출합니다.
   *
   * <p>
   * ■ BE 처리 <br>
   * - Google OAuth 토큰을 revoke 처리(best-effort)하고 관련 credential과 busy_day 데이터를 삭제합니다. <br>
   * - 연동이 해제되어도 사용자가 직접 입력한 정기·개별 일정 데이터는 그대로 유지됩니다.
   */
  @Operation(summary = "Google Calendar 연동 해제")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "연동 해제 성공",
          useReturnTypeSchema = true),
      @ApiResponse(
          responseCode = "401",
          description = "액세스 토큰 없음·무효(AUTH_INVALID_TOKEN)·만료(AUTH_EXPIRED)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "GOOGLE_CALENDAR_NOT_CONNECTED (미연동 상태에서 해제 요청)",
          content = @Content(
              schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping
  ResponseEntity<SuccessResponse<UserSummaryResponse>> disconnect(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = googleCalendarService.disconnect(userId);
    return ResponseEntity.ok(SuccessResponse.of(response));
  }
}
