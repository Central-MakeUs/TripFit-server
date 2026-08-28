package com.tripfit.tripfit.common.exception;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.FieldError;
import com.tripfit.tripfit.common.logging.PiiMasker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

  @ExceptionHandler({
      HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorResponse> handleClientInputError(Exception exception) {
    ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }

  private List<FieldError> toFieldErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors().stream()
        .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
        .toList();
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
    log.error(
        "Unhandled exception reached GlobalExceptionHandler",
        PiiMasker.maskThrowable(exception));
    ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }
}
