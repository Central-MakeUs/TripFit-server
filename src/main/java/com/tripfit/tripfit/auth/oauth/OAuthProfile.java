package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 토큰 검증 결과. verifier → AuthService 경계 DTO")
public record OAuthProfile(
    @Schema(description = "소셜 제공자") SocialProvider provider,

    @Schema(
        description = "provider 고유 사용자 ID → user.social_id",
        example = "1234567890") String providerUserId,

    @Schema(
        description = "이메일. UNIQUE·식별 키 아님",
        nullable = true,
        example = "user@example.com") String email,

    @Schema(
        description = "표시명. 미제공 시 null",
        nullable = true,
        example = "홍길동") String nickname,

    @Schema(
        description = "provider 프로필 이미지 URL (A안 passthrough). Apple null",
        nullable = true,
        example = "https://lh3.googleusercontent.com/a/example") String profileImageUrl,

    @Schema(
        description = "APPLE 전용. id_token aud 검증 시 실제로 매칭된 client_id(Bundle ID 또는 Services ID). "
            + "탈퇴 시 revoke 호출에 동일 client_id를 재사용하기 위해 credential 저장까지 전달됨. GOOGLE/KAKAO는 항상 null",
        nullable = true,
        example = "com.tripfit.app") String appleMatchedClientId
) {
}
