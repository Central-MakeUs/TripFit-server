package com.tripfit.tripfit.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "API 성공 응답 envelope")
public record SuccessResponse<T>(
    @Schema(description = "응답 본문") T data,

    @Schema(description = "에러 메시지 (성공 시 null)", nullable = true) String message,

    @Schema(description = "에러 코드 (성공 시 null)", nullable = true) String code
) {

  // 일반 성공 — message/code null
  public static <T> SuccessResponse<T> of(T data) {
    return new SuccessResponse<>(data, null, null);
  }
}
