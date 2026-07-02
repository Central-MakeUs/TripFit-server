package com.tripfit.tripfit.auth.jwt;

import java.util.UUID;

public record AccessTokenClaims(
    UUID userId,
    String jti
) {
}
