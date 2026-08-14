package com.tripfit.tripfit.auth.jwt;

import java.time.Instant;
import java.util.UUID;

// access JWT 파싱 결과 — jti는 logout/폐기 검사용, expiresAt은 블랙리스트 등록 시 남은 TTL 계산용
public record AccessTokenClaims(
    UUID userId,
    String jti,
    Instant expiresAt
) {
}
