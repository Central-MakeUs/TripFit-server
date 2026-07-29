package com.tripfit.tripfit.notification.event;

import java.util.UUID;

// BR-NOTI-002 — 여행방 정원 도달 시 방장에게 발송(D11). TripCommandService.joinTrip에서 커밋 후 발행
public record AllMembersSubmittedEvent(
    UUID tripId
) {
}
