package com.tripfit.tripfit.trip.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "여행방을 찾을 수 없습니다."),
  TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_ACCESS_DENIED", "여행방 참여 권한이 없습니다."),
  TRIP_FORBIDDEN(HttpStatus.FORBIDDEN, "TRIP_FORBIDDEN", "여행방 방장만 수행할 수 있습니다."),
  INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND", "초대 코드를 찾을 수 없습니다."),
  TRIP_NOT_ONGOING(HttpStatus.CONFLICT, "TRIP_NOT_ONGOING", "조율 중인 여행방만 수정·제출할 수 있습니다."),
  TRIP_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "TRIP_ALREADY_CONFIRMED", "일정이 확정된 여행방에는 참여할 수 없습니다."),
  TRIP_CANCELED(HttpStatus.CONFLICT, "TRIP_CANCELED", "취소된 여행방에는 참여할 수 없습니다."),
  TRIP_TERMINATED(HttpStatus.CONFLICT, "TRIP_TERMINATED", "종료된 여행방에는 참여할 수 없습니다."),
  TRIP_MEMBER_FULL(HttpStatus.CONFLICT, "TRIP_MEMBER_FULL", "참여 인원이 가득 찼습니다."),
  TRIP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_MEMBER_NOT_FOUND", "여행방 참여자를 찾을 수 없습니다."),
  CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "CANNOT_REMOVE_OWNER", "방장은 내보낼 수 없습니다.");

  private final HttpStatus httpStatus;

  private final String code;

  private final String message;

  TripErrorCode(HttpStatus httpStatus, String code, String message) {
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
