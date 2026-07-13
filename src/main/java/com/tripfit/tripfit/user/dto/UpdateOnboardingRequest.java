package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선택 온보딩 boolean PATCH 요청 (전송한 필드만 갱신)")
public record UpdateOnboardingRequest(
    @Schema(
        description = "Google Calendar OAuth 연동 성공 시 true. 미전송 시 유지",
        nullable = true,
        example = "false") Boolean isGoogleCalendarConnected,

    @Schema(
        description = "user_condition 저장 시 true. 미전송 시 유지",
        nullable = true,
        example = "false") Boolean isScheduleRegistered,

    @Schema(
        description = "선택 온보딩 전체 완료 시 true. 미전송 시 유지",
        nullable = true,
        example = "true") Boolean isOptionalOnboardingCompleted
) {
}
