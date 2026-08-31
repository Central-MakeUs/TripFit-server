package com.tripfit.tripfit.trip.event;

import java.util.UUID;

// BR-NOTI-001 — 참여자 join 완료 시 방장에게 발송. TripCommandService.joinTrip에서 커밋 후 발행
public record TripJoinCompletedEvent(
    UUID tripId,
    UUID joinedMemberUserId
) {
}
