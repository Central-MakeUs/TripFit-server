package com.tripfit.tripfit.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요청 필드의 유효성 검증 오류 정보입니다.")
public record FieldError(
    @Schema(description = "검증에 실패한 필드의 이름입니다.", example = "token") String field,

    @Schema(description = "검증 실패에 대한 상세 메시지입니다.", example = "must not be blank") String message
) {
}
