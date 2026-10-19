package com.tripfit.tripfit.notification.service;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationType;
import java.util.Map;
import java.util.UUID;

// 커밋 후 발송 작업으로 넘기는 값이다. 트랜잭션 밖에서 쓰이므로 엔티티 대신 기본 값만 담는다.
public record NotificationPush(
    NotificationType type,
    Map<String, UUID> historyIdByToken,
    String title,
    String body,
    LandingType landingType,
    UUID tripId
) {
}
