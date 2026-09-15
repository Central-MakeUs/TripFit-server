package com.tripfit.tripfit.auth.service;

import java.util.UUID;

// RefreshTokenService.create()/rotate()가 발급한 토큰 — Redis에 저장된 값을 auth.service 패키지 내부에서만
// 주고받는 캐리어라 DTO(auth/dto)가 아니라 여기 둔다
record IssuedRefreshToken(
    String token,
    UUID userId,
    String familyId
) {
}
