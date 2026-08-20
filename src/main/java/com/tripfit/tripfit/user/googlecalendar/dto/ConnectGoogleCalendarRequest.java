package com.tripfit.tripfit.user.googlecalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google Calendar OAuth 연동 요청")
public record ConnectGoogleCalendarRequest(
    @Schema(
        description = "Google OAuth authorization code (앱·웹이 Google 동의 후 수신)",
        example = "4/0AeanS...",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String authorizationCode,

    @Schema(
        description = """
            브라우저 리다이렉트 방식으로 연동할 때만 필요한 redirect_uri 원문 — Google authorization code 교환 시 요청에 실제로 썼던 값과 정확히 일치해야 한다.

            네이티브 앱(serverAuthCode) 방식은 보내지 않아도 된다(값이 있어도 무시).
            """,
        example = "https://tripfit.online/settings/google-calendar/callback",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String redirectUri
) {
}
