package com.tripfit.tripfit.common.exception;

import com.tripfit.tripfit.common.api.ErrorResponse;
import com.tripfit.tripfit.common.api.FieldError;
import com.tripfit.tripfit.common.logging.PiiMasker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
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

  // 다른 요청이 같은 데이터를 먼저 고쳐서 저장에 실패한 경우다. 재시도를 거는 유스케이스라면 여러 번
  // 다시 시도하고도 끝내지 못했을 때 여기까지 올라온다. 서버 오류가 아니라 같은 순간에 요청이 몰린
  // 상황이므로 500 대신 409로 답해, 클라이언트가 다시 시도하면 된다는 것을 알린다.
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ErrorResponse> handleVersionConflict(OptimisticLockingFailureException exception) {
    log.warn("Version conflict remained after retries: {}", exception.getMessage());
    ErrorCode errorCode = CommonErrorCode.CONCURRENT_MODIFICATION;
    return ResponseEntity.status(errorCode.getHttpStatus())
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
