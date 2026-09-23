package com.tripfit.tripfit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = """
    Apple Server-to-Server Notification 요청입니다.
    - POST /auth/apple/notifications
    - Apple 서버가 직접 호출합니다.
    """)
public record AppleNotificationRequest(
    @Schema(
        description = """
            Apple이 서명한 JWT 페이로드입니다.
            - 서명 및 audience 검증 후 내부 events 클레임을 파싱하여 처리합니다.
            """,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String payload
) {
}
