package com.tripfit.tripfit.user.schedule.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ScheduleErrorCode implements ErrorCode {
  REGULAR_SCHEDULE_REQUIRED(HttpStatus.FORBIDDEN, "REGULAR_SCHEDULE_REQUIRED", "정기 일정 등록이 필요합니다."),
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
