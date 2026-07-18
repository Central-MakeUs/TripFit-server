package com.tripfit.tripfit.user.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "사용자·온보딩 에러 코드")
public enum UserErrorCode implements ErrorCode {
  @Schema(description = "성·이름이 아직 입력되지 않음 — 여행방 생성·참여 전 필요")
  PROFILE_NAME_REQUIRED(HttpStatus.FORBIDDEN, "PROFILE_NAME_REQUIRED", "성·이름 입력이 필요합니다."),

  @Schema(description = "방 입장 조건 미충족 — 일정≥1건 또는 전부 free 필요")
  SCHEDULE_ENTRY_REQUIRED(HttpStatus.FORBIDDEN, "SCHEDULE_ENTRY_REQUIRED", "방 입장을 위해 일정을 등록하거나 전부 free를 확인해야 합니다."),

  @Schema(description = "이 여행방 일정 확인 미완료 — 방장은 schedule/confirm 필요, 방 입장 불가")
  SCHEDULE_CONFIRM_REQUIRED(HttpStatus.FORBIDDEN, "SCHEDULE_CONFIRM_REQUIRED", "이 여행방 일정 확인을 완료해야 입장할 수 있습니다.");

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
