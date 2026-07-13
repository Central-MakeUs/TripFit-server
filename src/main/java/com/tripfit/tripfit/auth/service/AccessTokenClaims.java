package com.tripfit.tripfit.auth.service;

import java.util.UUID;

public record AccessTokenClaims(
    UUID userId,
    String jti
) {
}
