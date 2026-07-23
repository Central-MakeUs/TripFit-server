package com.tripfit.tripfit.user.googlecalendar.client;

import java.time.Instant;

public record GoogleOAuthTokenResponse(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt
) {
}
