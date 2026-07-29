package com.tripfit.tripfit.notification.repository;

import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

  Optional<UserDeviceToken> findByToken(String token);

  boolean existsByTokenAndUser_Id(String token, UUID userId);

  void deleteByTokenAndUser_Id(String token, UUID userId);

  // FCM 무효 토큰 자동 정리 대상
  void deleteByTokenIn(Collection<String> tokens);

  // 알림 발송 대상 유저들의 등록 토큰 전체 — FCM 멀티캐스트 페이로드 SSOT
  @Query("SELECT t.token FROM UserDeviceToken t WHERE t.user.id IN :userIds")
  List<String> findTokensByUserIdIn(@Param("userIds") Collection<UUID> userIds);
}
