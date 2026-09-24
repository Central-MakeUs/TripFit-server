package com.tripfit.tripfit.user.schedule.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "일정 관련 에러 코드입니다.")
public enum ScheduleErrorCode implements ErrorCode {
  @Schema(description = "요청한 정기 일정을 찾을 수 없거나 본인 소유의 일정이 아닙니다.")
  REGULAR_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "REGULAR_SCHEDULE_NOT_FOUND", "정기 일정을 찾을 수 없습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  ScheduleErrorCode(HttpStatus httpStatus, String code, String message) {
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
