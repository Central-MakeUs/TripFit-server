package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.config.JwtProperties;
import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.repository.RefreshTokenRepository;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;

  private final JwtProperties jwtProperties;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProperties = jwtProperties;
  }

  // 사용자 ID 기준으로 새 리프레시 토큰을 발급해 저장함
  @Transactional
  public RefreshToken create(UUID userId) {
    // 1. 토큰 값과 토큰 패밀리 식별자를 새로 생성함 (wave 4 RTR에서 rotation·reuse 탐지)
    String token = UUID.randomUUID().toString();
    String familyId = UUID.randomUUID().toString();

    // 2. 설정된 만료 일수를 반영해 저장용 엔티티를 생성함
    LocalDateTime expiresAt =
        LocalDateTime.now().plusDays(jwtProperties.getRefreshExpirationDays());
    return refreshTokenRepository.save(new RefreshToken(userId, token, familyId, expiresAt));
  }

  // 리프레시 토큰의 존재 여부와 사용 가능 상태를 검증함
  @Transactional(readOnly = true)
  public RefreshToken validate(String tokenValue) {
    RefreshToken refreshToken =
        refreshTokenRepository
            .findByToken(tokenValue)
            .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH));
    if (refreshToken.isRevoked() || refreshToken.isExpired()) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH);
    }
    return refreshToken;
  }

  // 주어진 리프레시 토큰 값을 저장소에서 삭제함
  @Transactional
  public void delete(String tokenValue) {
    refreshTokenRepository.deleteByToken(tokenValue);
  }

  // 만료된 리프레시 토큰만 선별해서 저장소에서 정리함
  @Transactional
  public void deleteExpired(String tokenValue) {
    refreshTokenRepository
        .findByToken(tokenValue)
        .ifPresent(
            refreshToken -> {
              if (refreshToken.isExpired()) {
                refreshTokenRepository.delete(refreshToken);
              }
            });
  }
}
