package com.tripfit.tripfit.notification.event;

import java.util.UUID;

// BR-NOTI-009 — 방장이 확정 취소 시 참여자(방장 제외)에게 발송. #13 취소 API 구현 후 해당 서비스에서 발행 예정(현재 미발행)
public record TripConfirmCanceledEvent(
    UUID tripId
) {
}
