package com.tripfit.tripfit.notification.event;

import java.util.List;
import java.util.UUID;

// BR-NOTI-005 — 매월 1·15일 09:00(KST) 게이트 통과 사용자 배치에 발송. ScheduleReminderBatch에서 커밋 후 발행
public record ScheduleReminderEvent(
    List<UUID> userIds,
    int month
) {
}
