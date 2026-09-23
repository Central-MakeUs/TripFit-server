package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "온보딩 과정에서 최초로 성과 이름을 등록하는 요청입니다. (PATCH /users/onboarding/name)")
public record OnboardingNameRequest(
    @Schema(
        description = "사용자의 이름입니다. 공백은 허용되지 않습니다.",
        example = "길동",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String firstName,

    @Schema(
        description = "사용자의 성입니다. 공백은 허용되지 않습니다.",
        example = "홍",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String lastName
) {
}
