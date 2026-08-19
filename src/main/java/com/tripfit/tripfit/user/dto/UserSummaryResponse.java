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
            정기 일정이 1건 이상 있는지 (DB 컬럼 없음, 조회 시 계산).
            개별 일정은 세지 않는다 — 일정 확인 화면에서 "정기 일정이 있나요?" 질문을 띄울지 판정하는 값.
            true: 정기 일정 생성. false: 정기 일정이 0건(개별 일정만 있어도 false).
            일정 CRUD 응답에는 미포함 — GET /auth/me 등 재호출.
            """,
        example = "false") boolean hasRegularSchedule,

    @Schema(
        description = """
            정기 또는 개별 일정이 1건 이상 있는지 (DB 컬럼 없음, 조회 시 계산).
            true: 정기 일정 첫 생성 또는 개별 일정 첫 저장.
            false: 두 종류 일정 row가 모두 0건.
            정기·개별을 뭉뚱그린 값이라 정기 일정 유무를 판정할 수 없다 — 그 판정에는 hasRegularSchedule을 쓴다.
            일정 CRUD 응답에는 미포함 — GET /auth/me 등 재호출.
            """,
        example = "false") boolean hasPreSchedule,

    @Schema(
        description = "알림 수신 여부(BR-USER-005). default true. PATCH /users/profile로 변경",
        example = "true") boolean notificationEnabled
) {
}
