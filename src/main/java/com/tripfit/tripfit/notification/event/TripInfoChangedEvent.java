package com.tripfit.tripfit.notification.event;

import java.util.UUID;

// BR-NOTI-003 — patchTrip으로 실제 값이 바뀐 경우만 참여자(방장 제외)에게 발송(D12). TripCommandService.patchTrip에서 커밋 후
// 발행
public record TripInfoChangedEvent(
    UUID tripId
) {
}
