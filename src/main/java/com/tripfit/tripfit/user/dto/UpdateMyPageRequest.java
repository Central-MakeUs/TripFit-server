package com.tripfit.tripfit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "마이페이지 이름 PATCH 요청")
public record UpdateMyPageRequest(
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
