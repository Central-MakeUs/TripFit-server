package com.tripfit.tripfit.common.exception;

import org.springframework.http.HttpStatus;

// API 에러 계약 — HTTP status + machine code + 사용자 message (docs/architecture/api-response.md)
public interface ErrorCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
