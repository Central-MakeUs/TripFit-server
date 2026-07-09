package com.tripfit.tripfit.auth.dto;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 API용 사용자 요약 (Entity 전체 노출 금지)")
public record UserSummaryResponse(
    @Schema(description = "TripFit 사용자 ID", example = "1")
    Long id,
    @Schema(description = "소셜 계정 이메일. 미제공 시 null", nullable = true, example = "user@example.com")
    String email,
    @Schema(description = "UI 표시명", example = "홍길동")
    String nickname,
    @Schema(description = "프로필 이미지 URL. wave 1: provider CDN URL. wave 4: TripFit S3 URL 예정",
        nullable = true, example = "https://lh3.googleusercontent.com/a/example")
    String profileImageUrl,
    @Schema(description = "로그인에 사용한 소셜 제공자")
    SocialProvider provider
) {
}
