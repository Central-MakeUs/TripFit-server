package com.tripfit.tripfit.user.googlecalendar.controller;

import com.tripfit.tripfit.auth.jwt.AuthorizedUser;
import com.tripfit.tripfit.common.api.ApiResponse;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.googlecalendar.dto.ConnectGoogleCalendarRequest;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
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

  @Operation(
      summary = "Google Calendar 연동",
      description = """
          목적: Google OAuth authorization code로 Calendar 읽기 권한을 연동한다.

          호출 시점: 온보딩·설정 화면에서 Google Calendar 연동 버튼 완료 직후.

          전제: TripFit 로그인(JWT) 상태. 앱·웹이 Google OAuth 동의 후 authorization code를 전달한다.

          결과: isGoogleCalendarConnected=true로 갱신된 UserSummary. 연동 직후 freeBusy 1회 sync.

          주요 에러: GOOGLE_CALENDAR_CONNECT_FAILED — code 교환·Google API 실패
          """)
  @PostMapping
  ResponseEntity<ApiResponse<UserSummaryResponse>> connect(
      @AuthorizedUser UUID userId,
      @Valid @RequestBody ConnectGoogleCalendarRequest request) {
    UserSummaryResponse response =
        googleCalendarService.connect(userId, request.authorizationCode());
    return ResponseEntity.ok(ApiResponse.of(response));
  }

  @Operation(
      summary = "Google Calendar 연동 해제",
      description = """
          목적: Google Calendar 연동을 의도적으로 해제한다.

          호출 시점: 설정·온보딩에서 연동 해제 선택 시.

          전제: isGoogleCalendarConnected=true.

          결과: revoke(best-effort) 후 credential·busy_day 삭제, flag=false UserSummary. 정기·개별 일정은 유지.

          주요 에러: GOOGLE_CALENDAR_NOT_CONNECTED — 미연동 상태에서 해제 요청
          """)
  @DeleteMapping
  ResponseEntity<ApiResponse<UserSummaryResponse>> disconnect(@AuthorizedUser UUID userId) {
    UserSummaryResponse response = googleCalendarService.disconnect(userId);
    return ResponseEntity.ok(ApiResponse.of(response));
  }
}
