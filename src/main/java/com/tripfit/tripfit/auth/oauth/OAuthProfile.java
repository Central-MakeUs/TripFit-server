package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 토큰 검증 결과 정보입니다. (인증 과정에서 사용되는 경계 객체입니다)")
public record OAuthProfile(
    @Schema(description = "로그인에 사용된 소셜 제공자 정보입니다.") SocialProvider provider,

    @Schema(
        description = "소셜 제공자에서 발급한 고유 사용자 ID입니다.",
        example = "1234567890") String providerUserId,

    @Schema(
        description = "소셜 계정의 이메일입니다. 고유한 값이지만 식별 키로 사용되지는 않습니다.",
        nullable = true,
        example = "user@example.com") String email,

    @Schema(
        description = "사용자의 표시명입니다. 소셜 측에서 제공하지 않은 경우 null입니다.",
        nullable = true,
        example = "홍길동") String nickname,

    @Schema(
        description = "소셜 제공자의 프로필 이미지 URL입니다. Apple 로그인 시에는 null이 될 수 있습니다.",
        nullable = true,
        example = "https://lh3.googleusercontent.com/a/example") String profileImageUrl,

    @Schema(
        description = "APPLE 전용. id_token aud 검증 시 실제로 매칭된 client_id(Bundle ID 또는 Services ID). "
            + "탈퇴 시 revoke 호출에 동일 client_id를 재사용하기 위해 credential 저장까지 전달됨. GOOGLE/KAKAO는 항상 null",
        nullable = true,
        example = "com.tripfit.app") String appleMatchedClientId
) {
}
