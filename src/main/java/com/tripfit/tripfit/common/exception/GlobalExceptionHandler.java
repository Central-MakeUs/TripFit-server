package com.tripfit.tripfit.common.exception;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.FieldError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 도메인·검증 예외만 envelope로 변환 — Filter 경로 401은 AuthErrorResponseWriter가 담당
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(TripFitException.class)
  ResponseEntity<ErrorResponse> handleTripFitException(TripFitException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    // 커스텀 message가 있으면 ErrorCode 기본 문구보다 우선
    String message =
        exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage();
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(new ErrorResponse(errorCode.getCode(), message));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception) {
    ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
    List<FieldError> errors = toFieldErrors(exception.getBindingResult());
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors));
  }

  private List<FieldError> toFieldErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors().stream()
        .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
        .toList();
  }

  // 위 핸들러가 못 잡는 나머지 전부(DB·NPE 등) — 원인을 로그로 남기고 500 envelope로 통일해 401 오인 마스킹을 막음
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
    log.error("Unhandled exception reached GlobalExceptionHandler", exception);
    ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }
}
