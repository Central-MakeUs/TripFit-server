package com.tripfit.tripfit.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 제공자입니다.")
public enum SocialProvider {
  @Schema(description = "카카오 계정입니다.")
  KAKAO,
  @Schema(description = "구글 계정입니다.")
  GOOGLE,
  @Schema(description = "애플 계정입니다.")
  APPLE
}
