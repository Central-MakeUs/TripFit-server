package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "온보딩 프로필(성·이름) 수정 요청. PATCH /users/profile")
// @formatter:off — record 컴포넌트 가독성(필드별 빈 줄·어노테이션 분리)
public record UpdateProfileRequest(
    @Schema(
        description = "이름 (공백 불가)",
        example = "길동",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    String firstName,

    @Schema(
        description = "성 (공백 불가)",
        example = "홍",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    String lastName
) {
}
// @formatter:on
