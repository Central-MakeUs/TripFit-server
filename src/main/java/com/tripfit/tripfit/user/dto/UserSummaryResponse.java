package com.tripfit.tripfit.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "사용자 요약. POST /auth/login · GET /auth/me · PATCH /users/profile · PATCH /users/my-page 응답 공통")
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
            정기 또는 개별 일정이 1건 이상 있는지 (DB 컬럼 없음, 조회 시 계산).
            true: 정기 일정 첫 생성 또는 개별 일정 첫 저장.
            false: 두 종류 일정 row가 모두 0건.
            일정 CRUD 응답에는 미포함 — GET /auth/me 등 재호출.
            """,
        example = "false") boolean hasPreSchedule,

    @Schema(
        description = """
            전부 free 선언 여부 (user.is_all_free 저장, 기본 false).
            방 입장 조건: hasPreSchedule OR isAllFree.
            true: 일정 없이 Skip+0행으로 create/join·confirm 시 설정.
            false: 사용자가 명시적으로 해제하거나, 일정 row가 생기면 hasPreSchedule으로 대체 가능.
            일정 CRUD 응답에는 미포함 — GET /auth/me 등 재호출.
            """,
        example = "false") boolean isAllFree
) {
}
