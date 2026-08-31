package com.tripfit.tripfit.trip.event;

import java.util.UUID;

// BR-NOTI-009 — 방장이 확정 취소 시 참여자(방장 제외)에게 발송. TripRecommendationService.unconfirm에서 커밋 후 발행
public record TripConfirmCanceledEvent(
    UUID tripId
) {
}
