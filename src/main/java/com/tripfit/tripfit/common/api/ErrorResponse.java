package com.tripfit.tripfit.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "API 에러 응답 envelope")
public record ErrorResponse(
    @Schema(description = "에러 코드", example = "AUTH_INVALID_TOKEN") String code,

    @Schema(description = "사용자용 에러 메시지", example = "유효하지 않은 토큰입니다.") String message,

    @Schema(
        description = "필드 검증 오류 목록 (validation 실패 시)",
        nullable = true) List<FieldError> errors
) {

  public ErrorResponse(String code, String message) {
    this(code, message, null);
  }
}
