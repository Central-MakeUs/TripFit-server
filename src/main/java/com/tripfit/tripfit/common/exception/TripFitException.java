package com.tripfit.tripfit.common.exception;

// 도메인 비즈니스 예외 — ErrorCode로 HTTP/code, message는 기본값 또는 생성자 override
public class TripFitException extends RuntimeException {

  private final ErrorCode errorCode;

  public TripFitException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  // Handler가 getMessage()를 ErrorCode 기본 문구보다 우선 사용
  public TripFitException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
