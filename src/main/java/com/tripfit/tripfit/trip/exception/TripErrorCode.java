package com.tripfit.tripfit.trip.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "여행방 도메인 에러 코드")
public enum TripErrorCode implements ErrorCode {
  @Schema(description = "여행방 없음·soft deleted")
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "여행방을 찾을 수 없습니다."),

  @Schema(description = "비참여자")
  TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_ACCESS_DENIED", "여행방 참여 권한이 없습니다."),

  @Schema(description = "방장만 가능")
  TRIP_FORBIDDEN(HttpStatus.FORBIDDEN, "TRIP_FORBIDDEN", "여행방 방장만 수행할 수 있습니다."),

  @Schema(description = "초대 코드 없음")
  INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND", "초대 코드를 찾을 수 없습니다."),

  @Schema(description = "ONGOING이 아닌 방 수정·내보내기·일정 확인")
  TRIP_NOT_ONGOING(HttpStatus.CONFLICT, "TRIP_NOT_ONGOING", "조율 중인 여행방만 수정·내보내기·일정 확인할 수 있습니다."),

  @Schema(description = "CONFIRMED 방 신규 join")
  TRIP_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "TRIP_ALREADY_CONFIRMED", "일정이 확정된 여행방에는 참여할 수 없습니다."),

  @Schema(description = "EXPIRED 방 신규 join")
  TRIP_EXPIRED(HttpStatus.CONFLICT, "TRIP_EXPIRED", "종료된 여행방에는 참여할 수 없습니다."),

  @Schema(description = "정원 초과 신규 join")
  TRIP_MEMBER_FULL(HttpStatus.CONFLICT, "TRIP_MEMBER_FULL", "참여 인원이 가득 찼습니다."),

  @Schema(description = "참여자 없음·이미 내보낸(soft-deleted) 멤버")
  TRIP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_MEMBER_NOT_FOUND", "여행방 참여자를 찾을 수 없습니다."),

  @Schema(description = "방장은 내보낼 수 없음")
  CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "CANNOT_REMOVE_OWNER", "방장은 내보낼 수 없습니다."),

  @Schema(description = "방장은 나갈 수 없음 — 방 삭제를 사용해야 함")
  TRIP_OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "TRIP_OWNER_CANNOT_LEAVE", "방장은 여행방을 나갈 수 없습니다. 여행방 삭제를 이용해주세요.");

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
