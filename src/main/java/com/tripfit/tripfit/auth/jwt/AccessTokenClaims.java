package com.tripfit.tripfit.auth.jwt;

import java.util.UUID;

// access JWT 파싱 결과
public record AccessTokenClaims(
    UUID userId
) {
}
