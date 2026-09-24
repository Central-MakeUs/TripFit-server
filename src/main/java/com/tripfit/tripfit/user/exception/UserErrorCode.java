package com.tripfit.tripfit.user.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "사용자 및 온보딩 관련 에러 코드입니다.")
public enum UserErrorCode implements ErrorCode {
  @Schema(description = "프로필 이름(성, 이름)이 아직 입력되지 않았습니다. 여행방을 생성하거나 참여하기 전에 이름을 먼저 등록해야 합니다.")
  PROFILE_NAME_REQUIRED(HttpStatus.FORBIDDEN, "PROFILE_NAME_REQUIRED", "성·이름 입력이 필요합니다."),

  @Schema(description = "해당 여행방의 일정 확인(activate)이 완료되지 않았습니다. 완료 전에는 방에 입장할 수 없습니다.")
  SCHEDULE_ACTIVATION_REQUIRED(HttpStatus.FORBIDDEN, "SCHEDULE_ACTIVATION_REQUIRED", "이 여행방 일정 확인을 완료해야 입장할 수 있습니다."),

  @Schema(description = "사전 일정 입력이 완료되지 않았습니다. 연차나 휴일 정보(사전 신청일)를 최소 1회 이상 저장해야 일정 확인을 완료할 수 있습니다.")
  PRE_SCHEDULE_REQUIRED(HttpStatus.FORBIDDEN, "PRE_SCHEDULE_REQUIRED", "사전 일정 입력을 완료해야 여행방에 입장할 수 있습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  UserErrorCode(HttpStatus httpStatus, String code, String message) {
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
