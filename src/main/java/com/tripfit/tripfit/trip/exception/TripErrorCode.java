package com.tripfit.tripfit.trip.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "여행방을 찾을 수 없습니다."),
  TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_ACCESS_DENIED", "여행방 참여 권한이 없습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  TripErrorCode(HttpStatus httpStatus, String code, String message) {
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
