package com.tripfit.tripfit.notification.dto;

import com.tripfit.tripfit.notification.domain.DeviceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    description = """
        디바이스 푸시 토큰 등록 및 갱신 요청입니다. (POST /notifications/device-tokens)
        """)
public record DeviceTokenRegisterRequest(
    @Schema(
        description = """
            FCM(Firebase Cloud Messaging) 디바이스 등록 토큰입니다.
            - 빈 값을 전송할 경우 에러가 발생합니다.
            """,
        example = "dEf3...",
        requiredMode = Schema.RequiredMode.REQUIRED) String token,

    @Schema(
        description = """
            해당 토큰이 발급된 기기의 OS/플랫폼 정보입니다.
            """,
        requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DeviceType deviceType
) {
}
