package com.tripfit.tripfit.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자의 요약 정보 응답입니다. (로그인, 마이페이지 조회, 프로필 수정 등에서 공통으로 사용됩니다)")
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

    @Schema(description = "로그인에 사용한 소셜 제공자입니다.") SocialProvider provider,

    @Schema(
        description = "구글 캘린더 연동 여부입니다.",
        example = "false") boolean isGoogleCalendarConnected,

    @Schema(
        description = """
            사전 일정 정보를 한 번이라도 모두 입력했는지 여부입니다.
            - 연차/휴일 정보에 사전 신청일이 저장되었는지를 기준으로 계산됩니다.
            - true: 수정 화면 ("일정 변경이 있나요?")으로 진입
            - false: 최초 입력 화면 ("정기 일정이 있나요?")으로 진입
            - 참고: 입력은 끝냈지만 등록한 일정이 아예 없는 경우라도 true를 반환합니다.
            """,
        example = "false") boolean hasCompletedPreSchedule,

    @Schema(
        description = "알림 수신 여부입니다. (기본값: true)",
        example = "true") boolean notificationEnabled
) {
}
