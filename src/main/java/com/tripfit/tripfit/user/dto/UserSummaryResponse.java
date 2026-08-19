package com.tripfit.tripfit.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "사용자 요약. POST /auth/login · GET /auth/me · PATCH /users/onboarding/name · PATCH /users/profile 응답 공통")
public record UserSummaryResponse(
    @Schema(description = "TripFit 사용자 ID (UUID v4)",
        example = "550e8400-e29b-41d4-a716-446655440000") UUID id,

    @Schema(
        description = "소셜 계정 이메일. provider가 제공하지 않으면 null",
        nullable = true,
        example = "user@example.com") String email,

    @Schema(
        description = "사용자가 입력한 이름. 미입력 시 null",
        nullable = true,
        example = "길동") String firstName,

    @Schema(
        description = "사용자가 입력한 성. 미입력 시 null",
        nullable = true,
        example = "홍") String lastName,

    @Schema(
        description = "소셜 provider 표시명. 프로필 입력 prefill용. 미제공 시 null",
        nullable = true,
        example = "홍길동") String nickname,

    @Schema(
        description = "프로필 이미지 URL. 현재는 소셜 provider CDN URL. 미제공 시 null",
        nullable = true,
        example = "https://lh3.googleusercontent.com/a/example") String profileImageUrl,

    @Schema(description = "로그인에 사용한 소셜 제공자") SocialProvider provider,

    @Schema(
        description = "Google Calendar OAuth 연동 여부",
        example = "false") boolean isGoogleCalendarConnected,

    @Schema(
        description = """
            사전 일정 정보를 한 번이라도 입력 완료했는지 (DB 컬럼 없음, 연차·휴일 정보의 사전 신청일 저장 여부에서 파생).
            false = 최초 입력 → "정기 일정이 있나요?" 화면부터. true = 갱신 입력 → "일정 변경이 있나요?" 화면부터.
            정기·개별 일정 row 수는 판정에 쓰지 않는다 — 입력을 끝냈지만 막힌 일정이 없는 사용자도 true다.
            PATCH /users/schedule/vacation-policy 저장 시 true가 되며, 탈퇴 후 재가입하면 false로 돌아간다.
            일정 CRUD 응답에는 미포함 — GET /auth/me 등 재호출.
            """,
        example = "false") boolean hasCompletedPreSchedule,

    @Schema(
        description = "알림 수신 여부(BR-USER-005). default true. PATCH /users/profile로 변경",
        example = "true") boolean notificationEnabled
) {
}
