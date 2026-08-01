package com.tripfit.tripfit.auth.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "인증 도메인 에러 코드")
public enum AuthErrorCode implements ErrorCode {
  @Schema(description = "잘못된 인증 요청·미지원 provider")
  AUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REQUEST", "잘못된 인증 요청입니다."),

  @Schema(description = "액세스 JWT(서버 발급) 무효 — Bearer 없음·서명 불일치·형식 오류")
  AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 인증 토큰입니다."),

  @Schema(description = "액세스 JWT 만료")
  AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED", "액세스 토큰이 만료되었습니다."),

  @Schema(description = "소셜 provider가 만료로 응답한 로그인 토큰")
  AUTH_SOCIAL_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_SOCIAL_TOKEN_EXPIRED", "소셜 로그인 토큰이 만료되었습니다. 다시 로그인해 주세요."),

  @Schema(description = "만료 외 소셜 로그인 토큰 무효 — 서명·audience·형식 오류 등")
  AUTH_SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_SOCIAL_TOKEN_INVALID", "유효하지 않은 소셜 로그인 토큰입니다."),

  @Schema(description = "소셜 provider API 접근 실패 — 네트워크·타임아웃·provider 장애")
  AUTH_SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SOCIAL_PROVIDER_UNAVAILABLE", "소셜 로그인 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),

  @Schema(description = "리프레시 토큰 없음·만료·폐기")
  AUTH_INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH", "유효하지 않은 리프레시 토큰입니다."),

  @Schema(description = "인증됐으나 권한 없음")
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
