package com.tripfit.tripfit.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요청 필드 검증 오류")
public record FieldError(
    @Schema(description = "검증 실패 필드명", example = "token") String field,

    @Schema(description = "검증 실패 메시지", example = "must not be blank") String message
) {
}
