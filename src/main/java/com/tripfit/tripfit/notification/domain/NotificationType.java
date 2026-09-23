package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시스템에서 발송되는 푸시 알림의 종류입니다.")
public enum NotificationType {
  @Schema(
      description = """
          여행방 참여 완료 알림입니다.
          - 일반 멤버가 방에 join을 완료했을 때 방장에게 발송됩니다.
          """)
  JOIN_COMPLETED,

  @Schema(
      description = """
          전원 일정 제출 완료 알림입니다.
          - 여행방 모집 정원에 도달했을 때 방장에게 발송됩니다.
          """)
  ALL_MEMBERS_SUBMITTED,

  @Schema(
      description = """
          여행 정보 변경 알림입니다.
          - 방장이 여행방 메타 정보(이름, 기간 등)를 수정했을 때 방장을 제외한 참여자들에게 발송됩니다.
          """)
  TRIP_INFO_CHANGED,

  @Schema(
      description = """
          여행 일정 확정 알림입니다.
          - 방장이 추천 후보나 직접 입력으로 날짜를 확정했을 때 방장을 제외한 참여자들에게 발송됩니다.
          """)
  TRIP_CONFIRMED,

  @Schema(
      description = """
          여행 일정 확정 취소 알림입니다.
          - 방장이 기존에 확정했던 일정을 취소하여 다시 조율 상태(ONGOING)로 변경했을 때 방장을 제외한 참여자들에게 발송됩니다.
          """)
  TRIP_CONFIRM_CANCELED,

  @Schema(
      description = """
          정기 일정 등록 리마인드 알림입니다.
          - 매월 1일, 15일 09:00(KST)에 전체 사용자를 대상으로 발송됩니다.
          """)
  SCHEDULE_REMINDER
}
