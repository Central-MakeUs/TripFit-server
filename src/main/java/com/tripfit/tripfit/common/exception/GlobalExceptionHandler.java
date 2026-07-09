package com.tripfit.tripfit.common.exception;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.FieldError;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(TripFitException.class)
  ResponseEntity<ErrorResponse> handleTripFitException(TripFitException exception) {
    ErrorCode errorCode = exception.getErrorCode();
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
}
