package com.tripfit.tripfit.auth.jwt;

import java.util.UUID;

// access JWT 파싱 결과 — jti는 logout/폐기 검사용
public record AccessTokenClaims(
    UUID userId,
    String jti
) {
}
