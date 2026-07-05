package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.config.JwtProperties;
import com.tripfit.tripfit.auth.repository.RefreshToken;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public RefreshToken create(Long userId) {
		String token = UUID.randomUUID().toString();
		String familyId = UUID.randomUUID().toString();
		LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtProperties.getRefreshExpirationDays());
		return refreshTokenRepository.save(new RefreshToken(userId, token, familyId, expiresAt));
	}

	@Transactional(readOnly = true)
	public RefreshToken validate(String tokenValue) {
		RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
				.orElseThrow(() -> new TripFitException(ErrorCode.AUTH_INVALID_REFRESH));
		if (refreshToken.isRevoked() || refreshToken.isExpired()) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_REFRESH);
		}
		return refreshToken;
	}

	@Transactional
	public void delete(String tokenValue) {
		refreshTokenRepository.deleteByToken(tokenValue);
	}

	@Transactional
	public void deleteExpired(String tokenValue) {
		refreshTokenRepository.findByToken(tokenValue).ifPresent(refreshToken -> {
			if (refreshToken.isExpired()) {
				refreshTokenRepository.delete(refreshToken);
			}
		});
	}
}
