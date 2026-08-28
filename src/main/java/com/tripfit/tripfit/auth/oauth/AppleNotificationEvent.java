package com.tripfit.tripfit.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "Apple S2S notification 내부 events 클레임(JSON 문자열) 파싱 결과. verifier → AppleNotificationService 경계 DTO")
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleNotificationEvent(
    @Schema(
        description = "이벤트 종류. consent-revoked/account-delete/email-enabled/email-disabled 4종(2026-07 기준)",
        example = "consent-revoked") String type,

    @Schema(description = "Apple user identifier → user.social_id",
        example = "001234.abcd1234.5678") String sub,

    @Schema(description = "이벤트 발생 시각(epoch seconds)",
        nullable = true) @JsonProperty("event_time") Long eventTime,

    @Schema(
        description = "email-enabled/email-disabled 전용. 그 외 이벤트는 null",
        nullable = true,
        example = "user@privaterelay.appleid.com") String email,

    @Schema(
        description = "email-enabled/email-disabled 전용. Private Relay 여부, 그 외 이벤트는 null",
        nullable = true) @JsonProperty("is_private_email") Boolean isPrivateEmail
) {
}
