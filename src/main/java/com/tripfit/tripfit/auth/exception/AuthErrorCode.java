package com.tripfit.tripfit.auth.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "인증 도메인 관련 에러 코드입니다.")
public enum AuthErrorCode implements ErrorCode {
  @Schema(description = "인증 요청이 잘못되었거나 지원하지 않는 로그인 제공자입니다.")
  AUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REQUEST", "잘못된 인증 요청입니다."),

  @Schema(description = "서버에서 발급한 액세스 토큰이 유효하지 않습니다. (토큰 누락, 서명 불일치, 형식 오류 등)")
  AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 인증 토큰입니다."),

  @Schema(description = "액세스 토큰이 만료되었습니다.")
  AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED", "액세스 토큰이 만료되었습니다."),

  @Schema(description = "소셜 제공자 측에서 만료된 것으로 응답한 로그인 토큰입니다.")
  AUTH_SOCIAL_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_SOCIAL_TOKEN_EXPIRED", "소셜 로그인 토큰이 만료되었습니다. 다시 로그인해 주세요."),

  @Schema(description = "소셜 로그인 토큰이 유효하지 않습니다. (만료 외 서명, audience, 형식 오류 등)")
  AUTH_SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_SOCIAL_TOKEN_INVALID", "유효하지 않은 소셜 로그인 토큰입니다."),

  @Schema(description = "소셜 제공자 API에 접근할 수 없습니다. (네트워크 문제, 타임아웃, 제공자 측 장애 등)")
  AUTH_SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SOCIAL_PROVIDER_UNAVAILABLE", "소셜 로그인 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),

  @Schema(description = "리프레시 토큰이 없거나 만료되었거나 이미 폐기되었습니다.")
  AUTH_INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH", "유효하지 않은 리프레시 토큰입니다."),

  @Schema(
      description = "이미 폐기(rotate)된 리프레시 토큰이 재사용되었습니다. 탈취가 의심되어 해당 로그인 체인 전체가 무효화됩니다. 재로그인이 필요합니다.")
  AUTH_REFRESH_REUSE(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REUSE", "재사용된 리프레시 토큰이 감지되어 다시 로그인해야 합니다."),

  @Schema(description = "인증은 완료되었으나 요청을 수행할 권한이 없습니다.")
  AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "접근 권한이 없습니다."),

  @Schema(
      description = "APPLE 로그인이지만 인증 코드(authorizationCode)가 누락되었습니다. (탈퇴 시 Apple에 revoke 호출을 위해 필요합니다)")
  AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED", "Apple 로그인에는 authorizationCode가 필요합니다."),

  @Schema(
      description = "GOOGLE 로그인이지만 인증 코드(authorizationCode)가 누락되었습니다. (탈퇴 시 Google 로그인 동의 revoke 호출을 위해 필요합니다)")
  AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED", "Google 로그인에는 authorizationCode가 필요합니다."),

  @Schema(
      description = "Apple S2S 알림 페이로드 형식이 잘못되었습니다. (JWT 파싱 실패, JSON 오류, 필수 필드 누락 등)")
  AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD", "Apple 알림 payload 형식이 올바르지 않습니다."),

  @Schema(description = "Apple S2S 알림 JWT의 발급자(iss)가 유효하지 않습니다.")
  AUTH_APPLE_NOTIFICATION_ISSUER_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_APPLE_NOTIFICATION_ISSUER_INVALID", "Apple 알림 발급자 검증에 실패했습니다."),

  @Schema(
      description = "Apple S2S 알림 JWT의 대상(aud)이 서버에 설정된 Apple 정보와 일치하지 않습니다.")
  AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID", "Apple 알림 대상(audience) 검증에 실패했습니다."),

  @Schema(description = "Apple S2S 알림 JWT의 서명 검증에 실패했거나 만료되었습니다.")
  AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID", "Apple 알림 서명 검증에 실패했습니다.");

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
