package com.tripfit.tripfit.user.googlecalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google Calendar OAuth 연동 요청")
public record ConnectGoogleCalendarRequest(
    @Schema(
        description = "Google OAuth authorization code (앱·웹이 Google 동의 후 수신)",
        example = "4/0AeanS...",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String authorizationCode
) {
}
