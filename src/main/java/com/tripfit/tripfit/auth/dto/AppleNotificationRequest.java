package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    description = "Apple Server-to-Server Notification 요청. POST /auth/apple/notifications — Apple 서버가 직접 호출")
public record AppleNotificationRequest(
    @Schema(
        description = "Apple이 서명한 JWT — 서명·audience 검증 후 내부 events 클레임을 파싱해 처리",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String payload
) {
}
