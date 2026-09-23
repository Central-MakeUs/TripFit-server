package com.tripfit.tripfit.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = """
        API 에러 발생 시 공통 응답 봉투(Envelope) 포맷입니다.
        """)
public record ErrorResponse(
    @Schema(
        description = """
            서버에서 정의한 에러 코드입니다.
            """,
        example = "AUTH_INVALID_TOKEN") String code,

    @Schema(
        description = """
            사용자에게 노출 가능한 에러 메시지입니다.
            """,
        example = "유효하지 않은 토큰입니다.") String message,

    @Schema(
        description = """
            입력값 검증(Validation) 실패 시 발생하는 필드별 오류 목록입니다.
            """,
        nullable = true) List<FieldError> errors
) {

  public ErrorResponse(String code, String message) {

    this(code, message, null);
  }
}
