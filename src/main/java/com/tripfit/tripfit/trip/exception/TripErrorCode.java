package com.tripfit.tripfit.trip.exception;

import com.tripfit.tripfit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "여행방 도메인 관련 에러 코드입니다.")
public enum TripErrorCode implements ErrorCode {
  @Schema(description = "요청한 여행방을 찾을 수 없거나 이미 삭제된 상태입니다.")
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "여행방을 찾을 수 없습니다."),

  @Schema(description = "해당 여행방의 참여자가 아닙니다.")
  TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TRIP_ACCESS_DENIED", "여행방 참여 권한이 없습니다."),

  @Schema(description = "방장만 수행할 수 있는 권한입니다.")
  TRIP_FORBIDDEN(HttpStatus.FORBIDDEN, "TRIP_FORBIDDEN", "여행방 방장만 수행할 수 있습니다."),

  @Schema(description = "초대 코드가 존재하지 않습니다.")
  INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND", "초대 코드를 찾을 수 없습니다."),

  @Schema(description = "조율 중(ONGOING)이 아닌 여행방에서는 수정, 내보내기, 일정 확인 작업을 수행할 수 없습니다.")
  TRIP_NOT_ONGOING(HttpStatus.CONFLICT, "TRIP_NOT_ONGOING", "조율 중인 여행방만 수정·내보내기·일정 확인할 수 있습니다."),

  @Schema(description = "이미 일정이 확정된(CONFIRMED) 여행방에는 새로 참여할 수 없습니다.")
  TRIP_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "TRIP_ALREADY_CONFIRMED", "일정이 확정된 여행방에는 참여할 수 없습니다."),

  @Schema(description = "이미 종료된(EXPIRED) 여행방에는 새로 참여할 수 없습니다.")
  TRIP_EXPIRED(HttpStatus.CONFLICT, "TRIP_EXPIRED", "종료된 여행방에는 참여할 수 없습니다."),

  @Schema(description = "정원이 초과된 여행방에는 참여할 수 없습니다.")
  TRIP_MEMBER_FULL(HttpStatus.CONFLICT, "TRIP_MEMBER_FULL", "참여 인원이 가득 찼습니다."),

  @Schema(description = "참여자가 없거나 이미 내보내진 멤버입니다.")
  TRIP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_MEMBER_NOT_FOUND", "여행방 참여자를 찾을 수 없습니다."),

  @Schema(description = "방장은 여행방에서 내보낼 수 없습니다.")
  CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "CANNOT_REMOVE_OWNER", "방장은 내보낼 수 없습니다."),

  @Schema(description = "방장은 여행방을 스스로 나갈 수 없습니다. 여행방 삭제 기능을 사용해 주세요.")
  TRIP_OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "TRIP_OWNER_CANNOT_LEAVE", "방장은 여행방을 나갈 수 없습니다. 여행방 삭제를 이용해주세요."),

  @Schema(description = "아직 일정이 확정되지 않은 여행방에서는 확정 취소를 요청할 수 없습니다.")
  TRIP_NOT_CONFIRMED(HttpStatus.CONFLICT, "TRIP_NOT_CONFIRMED", "확정된 여행방만 확정을 취소할 수 있습니다."),

  @Schema(description = "확정 취소 사유(reason)가 누락되었거나, 기타 사유(OTHER)일 때 상세 설명(reasonDetail)이 없습니다.")
  INVALID_UNCONFIRM_REASON(HttpStatus.BAD_REQUEST, "INVALID_UNCONFIRM_REASON", "확정 취소 사유를 올바르게 입력해주세요."),

  @Schema(description = "존재하지 않는 추천 순위에 대해 조회, 확정, 피드백을 요청했습니다.")
  RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "추천 후보를 찾을 수 없습니다."),

  @Schema(
      description = "도움이 되지 않음(NOT_HELPFUL) 피드백에 사유가 누락되었거나, 기타 사유(OTHER)일 때 상세 설명(reasonDetail)이 없습니다.")
  INVALID_RECOMMENDATION_FEEDBACK(HttpStatus.BAD_REQUEST, "INVALID_RECOMMENDATION_FEEDBACK", "추천 피드백 사유를 올바르게 입력해주세요."),

  @Schema(description = "일정 확정 요청 시 추천 순위와 직접 입력 날짜 중 하나만 포함되어야 합니다.")
  INVALID_CONFIRM_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_CONFIRM_REQUEST", "추천 순위 또는 시작·종료일 중 하나만 입력해주세요."),

  @Schema(description = "직접 입력한 확정 날짜의 일수가 여행방에 설정된 기간(durationDays)과 일치하지 않습니다.")
  CONFIRM_DURATION_MISMATCH(HttpStatus.BAD_REQUEST, "CONFIRM_DURATION_MISMATCH", "확정 일정의 일수가 희망 여행 일수와 다릅니다.");

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
