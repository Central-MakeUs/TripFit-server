package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.repository.RefreshTokenRepository;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;

  private final JwtProperties jwtProperties;

  // 로그인 시 신규 로그인 체인(family)으로 리프레시 토큰을 발급해 저장함
  @Transactional
  public RefreshToken create(UUID userId) {
    return createInFamily(userId, UUID.randomUUID().toString());
  }

  // RTR — 제출된 토큰을 검증하고, 유효하면 같은 family로 새 토큰을 발급하며 기존 토큰은 즉시 폐기함(rotate).
  // 이미 폐기된 토큰이 재제출되면 탈취 재사용으로 간주해 family 전체를 폐기하고 AUTH_REFRESH_REUSE를 던짐
  @Transactional
  public RefreshToken rotate(String tokenValue) {
    RefreshToken current =
        refreshTokenRepository
            .findByToken(tokenValue)
            .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH));

    if (current.isRevoked()) {
      revokeFamily(current.getFamilyId());
      throw new TripFitException(AuthErrorCode.AUTH_REFRESH_REUSE);
    }
    if (current.isExpired()) {
      // 만료 토큰으로 재시도 시 row를 남겨두면 동일 토큰으로 반복 호출 가능 — 정리 후 401
      refreshTokenRepository.delete(current);
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH);
    }

    current.setRevokedAt(LocalDateTime.now());
    return createInFamily(current.getUserId(), current.getFamilyId());
  }

  private RefreshToken createInFamily(UUID userId, String familyId) {
    String token = UUID.randomUUID().toString();
    LocalDateTime expiresAt =
        LocalDateTime.now().plusDays(jwtProperties.getRefreshExpirationDays());
    return refreshTokenRepository.save(new RefreshToken(userId, token, familyId, expiresAt));
  }

  // 재사용 탐지 — 같은 로그인 체인에서 아직 안 죽은 토큰 전부를 폐기함(공격자가 새로 받아간 토큰 포함)
  private void revokeFamily(String familyId) {
    LocalDateTime now = LocalDateTime.now();
    refreshTokenRepository
        .findAllByFamilyIdAndRevokedAtIsNull(familyId)
        .forEach(refreshToken -> refreshToken.setRevokedAt(now));
  }

  // 주어진 리프레시 토큰 값을 저장소에서 삭제함
  @Transactional
  public void delete(String tokenValue) {
    refreshTokenRepository.deleteByToken(tokenValue);
  }

  // 회원 탈퇴 cascade — 해당 사용자의 리프레시 토큰을 전부 hard delete
  @Transactional
  public void revokeAllForUser(UUID userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
  }
}
