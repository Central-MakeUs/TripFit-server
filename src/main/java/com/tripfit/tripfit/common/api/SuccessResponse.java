package com.tripfit.tripfit.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = """
        API 성공 시 공통 응답 봉투(Envelope) 포맷입니다.
        """)
public record SuccessResponse<T>(
    @Schema(
        description = """
            실제 응답 데이터 본문입니다.
            """) T data,

    @Schema(
        description = """
            에러 메시지입니다.
            - 성공 시에는 null을 반환합니다.
            """,
        nullable = true) String message,

    @Schema(
        description = """
            에러 코드입니다.
            - 성공 시에는 null을 반환합니다.
            """,
        nullable = true) String code
) {

  public static <T> SuccessResponse<T> of(T data) {
    return new SuccessResponse<>(data, null, null);
  }
}
