package com.tripfit.tripfit.auth.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

	AUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REQUEST", "잘못된 인증 요청입니다."),
	AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 소셜 로그인 토큰입니다."),
	AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED", "액세스 토큰이 만료되었습니다."),
	AUTH_INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH", "유효하지 않은 리프레시 토큰입니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "접근 권한이 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	AuthErrorCode(HttpStatus httpStatus, String code, String message) {
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
