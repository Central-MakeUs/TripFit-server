package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByToken(String token);

	void deleteByToken(String token);

	void deleteAllByUserId(Long userId);
}
