package com.tripfit.tripfit.user.googlecalendar.service;

import java.time.Instant;

// GoogleCalendarService.resolveAccessToken()의 결과 — refresh가 일어났을 때만 캐시 갱신용 필드가 채워짐.
// DB 반영은 하지 않고 값만 담아 GoogleCalendarSyncPersistenceService로 넘긴다
record AccessTokenResolution(
    String accessToken,
    String refreshedAccessCiphertext,
    Instant refreshedAccessExpiresAt,
    String refreshedRefreshCiphertext
) {
}
