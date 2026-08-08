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

  // 알림 발송 대상 유저들의 (userId, token) — FCM data에 유저별 알림 이력 id를 매칭하기 위해 토큰 소유자와 함께 조회
  @Query("SELECT t.user.id AS userId, t.token AS token FROM UserDeviceToken t WHERE t.user.id IN :userIds")
  List<UserTokenView> findUserIdAndTokenByUserIdIn(@Param("userIds") Collection<UUID> userIds);

  // userId·token 프로젝션 — 알림 이력 id를 토큰별 FCM data에 매핑할 때 사용
  interface UserTokenView {
    UUID getUserId();

    String getToken();
  }
}
