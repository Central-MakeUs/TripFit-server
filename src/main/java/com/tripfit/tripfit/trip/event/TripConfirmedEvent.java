package com.tripfit.tripfit.trip.event;

import java.util.UUID;

// BR-NOTI-004 — 방장이 일정 확정 시 참여자(방장 제외)에게 발송. TripRecommendationService.confirmSchedule에서 커밋 후 발행
public record TripConfirmedEvent(
    UUID tripId
) {
}
