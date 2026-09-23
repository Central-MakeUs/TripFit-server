package com.tripfit.tripfit.user.googlecalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google Calendar 연동 요청입니다. (POST /users/settings/google-calendar)")
public record ConnectGoogleCalendarRequest(
    @Schema(
        description = """
            Google OAuth 인가 코드(authorization code)입니다.
            - 앱이나 웹에서 Google 권한 동의 후 수신한 코드입니다.
            """,
        example = "4/0AeanS...",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String authorizationCode,

    @Schema(
        description = """
            웹 브라우저 리다이렉트 방식으로 연동할 때만 필요한 redirect_uri입니다.
            - 인가 코드 교환 시 사용했던 값과 정확히 일치해야 합니다.
            - 네이티브 앱 연동 시에는 생략 가능합니다.
            """,
        example = "https://tripfit.online/settings/google-calendar/callback",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED) String redirectUri
) {
}
