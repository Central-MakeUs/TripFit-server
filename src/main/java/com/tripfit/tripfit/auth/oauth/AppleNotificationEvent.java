package com.tripfit.tripfit.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "Apple S2S 알림 내부의 events 클레임 파싱 결과입니다.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleNotificationEvent(
    @Schema(
        description = "이벤트 종류. consent-revoked/account-delete/email-enabled/email-disabled 4종(2026-07 기준)",
        example = "consent-revoked") String type,

    @Schema(description = "Apple 사용자 식별자입니다.",
        example = "001234.abcd1234.5678") String sub,

    @Schema(description = "이벤트 발생 시각(epoch seconds)",
        nullable = true) @JsonProperty("event_time") Long eventTime,

    @Schema(
        description = "이메일 관련 이벤트 전용 필드입니다. 그 외의 이벤트인 경우 null입니다.",
        nullable = true,
        example = "user@privaterelay.appleid.com") String email,

    @Schema(
        description = "이메일 관련 이벤트 시 Private Relay 사용 여부를 나타냅니다. 그 외의 이벤트인 경우 null입니다.",
        nullable = true) @JsonProperty("is_private_email") Boolean isPrivateEmail
) {
}
