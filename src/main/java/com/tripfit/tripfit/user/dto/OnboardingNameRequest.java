package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "온보딩 최초 성·이름 등록 요청. PATCH /users/onboarding/name")

public record OnboardingNameRequest(
    @Schema(
        description = "이름 (공백 불가)",
        example = "길동",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String firstName,

    @Schema(
        description = "성 (공백 불가)",
        example = "홍",
        requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String lastName
) {
}
