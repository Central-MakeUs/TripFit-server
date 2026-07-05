package com.tripfit.tripfit.common.exception;

public class TripFitException extends RuntimeException {

	private final ErrorCode errorCode;

	public TripFitException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public TripFitException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
