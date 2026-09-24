package com.tripfit.tripfit.notification.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "알림 도메인 관련 에러 코드입니다.")
public enum NotificationErrorCode implements ErrorCode {
  @Schema(description = "디바이스 토큰 값이 누락되었습니다.")
  NOTIFICATION_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "NOTIFICATION_TOKEN_REQUIRED", "디바이스 토큰 값이 필요합니다."),

  @Schema(description = "삭제하려는 디바이스 토큰이 없거나 본인의 토큰이 아닙니다.")
  NOTIFICATION_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_TOKEN_NOT_FOUND", "디바이스 토큰을 찾을 수 없습니다."),

  @Schema(description = "요청한 알림이 존재하지 않거나 본인의 알림이 아닙니다.")
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  NotificationErrorCode(HttpStatus httpStatus, String code, String message) {
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
