package com.tripfit.tripfit.common.exception;

import com.tripfit.tripfit.common.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(TripFitException.class)
	ResponseEntity<ErrorResponse> handleTripFitException(TripFitException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		String message = exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage();
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(new ErrorResponse(errorCode.getCode(), message));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse(ErrorCode.AUTH_INVALID_REQUEST.getCode(), ErrorCode.AUTH_INVALID_REQUEST.getMessage()));
	}
}
