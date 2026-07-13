package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByToken(String token);

  void deleteByToken(String token);

  void deleteAllByUserId(UUID userId);
}
