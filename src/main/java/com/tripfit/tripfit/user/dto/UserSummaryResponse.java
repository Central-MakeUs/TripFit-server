package com.tripfit.tripfit.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 요약 (login · GET /auth/me · PATCH profile/onboarding 공통)")
public record UserSummaryResponse(
    @Schema(description = "TripFit 사용자 ID (UUID v4)",
        example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(
        description = "소셜 계정 이메일. 미제공 시 null",
        nullable = true,
        example = "user@example.com")
    String email,

    @Schema(
        description = "유저 입력 이름. 미입력 시 null",
        nullable = true,
        example = "길동")
    String firstName,

    @Schema(
        description = "유저 입력 성. 미입력 시 null",
        nullable = true,
        example = "홍")
    String lastName,

    @Schema(
        description = "소셜 provider 표시명 (prefill용). 미제공 시 null",
        nullable = true,
        example = "홍길동")
    String nickname,

    @Schema(
        description = "프로필 이미지 URL. wave 1: provider CDN URL. wave 4: TripFit S3 URL 예정",
        nullable = true,
        example = "https://lh3.googleusercontent.com/a/example")
    String profileImageUrl,

    @Schema(description = "로그인에 사용한 소셜 제공자")
    SocialProvider provider,

    @Schema(
        description = "Google Calendar OAuth 연동 여부. 연동 성공 시만 true",
        example = "false")
    boolean isGoogleCalendarConnected,

    @Schema(
        description = "user_condition 저장 여부",
        example = "false")
    boolean isScheduleRegistered,

    @Schema(
        description = "선택 온보딩 전체 완료 여부",
        example = "false")
    boolean isOptionalOnboardingCompleted
) {
}
