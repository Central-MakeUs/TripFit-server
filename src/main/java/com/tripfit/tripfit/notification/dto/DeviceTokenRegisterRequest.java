package com.tripfit.tripfit.notification.dto;

import com.tripfit.tripfit.notification.domain.DeviceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "디바이스 토큰 등록·갱신 요청. POST /notifications/device-tokens")

public record DeviceTokenRegisterRequest(
    @Schema(
        description = "FCM 등록 토큰 값. 빈 값이면 NOTIFICATION_TOKEN_REQUIRED",
        example = "dEf3...",
        requiredMode = Schema.RequiredMode.REQUIRED) String token,

    @Schema(
        description = "토큰을 발급한 기기 플랫폼",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DeviceType deviceType
) {
}
