package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 종류 (BR-NOTI-001~005·009)")
public enum NotificationType {
  @Schema(description = "참여 완료 — 참여자 join 시 방장에게 발송(BR-NOTI-001)")
  JOIN_COMPLETED,

  @Schema(description = "전원 일정 제출 완료 — 여행방 정원 도달 시 방장에게 발송(BR-NOTI-002)")
  ALL_MEMBERS_SUBMITTED,

  @Schema(description = "여행 정보 변경 — 방장 patchTrip으로 실제 값이 바뀌면 참여자(방장 제외)에게 발송(BR-NOTI-003)")
  TRIP_INFO_CHANGED,

  @Schema(description = "일정 확정 — 방장 confirmSchedule 시 참여자(방장 제외)에게 발송(BR-NOTI-004)")
  TRIP_CONFIRMED,

  @Schema(description = "일정 확정 취소 — 방장이 확정을 취소하면 참여자(방장 제외)에게 발송(BR-NOTI-009)")
  TRIP_CONFIRM_CANCELED,

  @Schema(description = "일정 업데이트 리마인드 — 매월 1·15일 09:00(KST) 전체 사용자 대상 발송(BR-NOTI-005)")
  SCHEDULE_REMINDER
}
