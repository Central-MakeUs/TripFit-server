package com.tripfit.tripfit.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 제공자")
public enum SocialProvider {

  @Schema(description = "카카오")
  KAKAO,

  @Schema(description = "Google")
  GOOGLE,

  @Schema(description = "Apple Sign In")
  APPLE
}
