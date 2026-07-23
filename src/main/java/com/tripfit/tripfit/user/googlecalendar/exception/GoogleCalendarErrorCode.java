package com.tripfit.tripfit.user.googlecalendar.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "Google Calendar 연동 에러 코드")
public enum GoogleCalendarErrorCode implements ErrorCode {
  @Schema(description = "authorization code 교환·Google API 호출 실패")
  GOOGLE_CALENDAR_CONNECT_FAILED(HttpStatus.BAD_GATEWAY, "GOOGLE_CALENDAR_CONNECT_FAILED", "Google Calendar 연동에 실패했습니다."),

  @Schema(description = "연동되지 않은 상태에서 해제 요청")
  GOOGLE_CALENDAR_NOT_CONNECTED(HttpStatus.CONFLICT, "GOOGLE_CALENDAR_NOT_CONNECTED", "Google Calendar가 연동되어 있지 않습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  GoogleCalendarErrorCode(HttpStatus httpStatus, String code, String message) {
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
