package com.tripfit.tripfit.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "공통 에러 코드입니다.")
public enum CommonErrorCode implements ErrorCode {
  @Schema(description = "요청 값 검증에 실패했거나 비즈니스 입력 요구사항을 충족하지 못했습니다.")
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),

  @Schema(
      description = "같은 데이터를 고치는 다른 요청과 계속 부딪혀, 서버가 여러 번 다시 시도하고도 요청을 끝내지 못했습니다. 잠시 뒤 같은 요청을 다시 보내면 됩니다.")
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "요청이 동시에 처리되어 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."),

  @Schema(description = "예상치 못한 서버 내부 오류가 발생했습니다.")
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  CommonErrorCode(HttpStatus httpStatus, String code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
