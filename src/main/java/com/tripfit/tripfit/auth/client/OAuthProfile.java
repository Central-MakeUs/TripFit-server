package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * verifier → {@code AuthService} 경계 DTO (DB Entity 아님).
 */
@Schema(description = "소셜 토큰 검증 결과. verifier → AuthService 경계 DTO")
public record OAuthProfile(
    @Schema(description = "소셜 제공자")
    SocialProvider provider,
    @Schema(description = "provider 고유 사용자 ID → user.social_id", example = "1234567890")
    String providerUserId,
    @Schema(description = "이메일. UNIQUE·식별 키 아님", nullable = true, example = "user@example.com")
    String email,
    @Schema(description = "표시명. 미제공 시 null", nullable = true, example = "홍길동")
    String nickname,
    @Schema(description = "provider 프로필 이미지 URL (A안 passthrough). Apple null", nullable = true,
        example = "https://lh3.googleusercontent.com/a/example")
    String profileImageUrl
) {
}
