package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByToken(String token);

  // 재사용 탐지 시 같은 로그인 체인(family)에서 아직 안 죽은 행 전부를 폐기 대상으로 조회
  List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(String familyId);

  void deleteByToken(String token);

  void deleteAllByUserId(UUID userId);
}
