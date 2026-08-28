package com.tripfit.tripfit.user.googlecalendar.service;

import java.time.Instant;

record AccessTokenResolution(
    String accessToken,
    String refreshedAccessCiphertext,
    Instant refreshedAccessExpiresAt,
    String refreshedRefreshCiphertext
) {
}
