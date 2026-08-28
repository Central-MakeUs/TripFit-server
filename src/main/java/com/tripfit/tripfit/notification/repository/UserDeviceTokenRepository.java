package com.tripfit.tripfit.notification.repository;

import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

  Optional<UserDeviceToken> findByToken(String token);

  // 로그아웃 시 본인 토큰 해제 대상 — deleteByTokenIn과 동일한 이유(엔티티 단위 delete의
  // ObjectOptimisticLockingFailureException 레이스 회피)로 벌크 쿼리를 쓴다. FCM 무효 토큰 자동 정리가
  // 거의 동시에 같은 토큰을 지울 수 있다. 반환값(삭제된 행 수)으로 존재 여부를 판단
  @Modifying
  @Query("DELETE FROM UserDeviceToken t WHERE t.token = :token AND t.user.id = :userId")
  long deleteByTokenAndUser_Id(@Param("token") String token, @Param("userId") UUID userId);

  // FCM 무효 토큰 자동 정리 대상 — 여러 알림 이벤트가 짧은 시간 안에 같은 무효 토큰을 동시에 정리할 수 있어
  // 엔티티 단위 delete(파생 쿼리 기본 동작) 대신 벌크 쿼리를 쓴다. 엔티티 단위 delete는 삭제 직전 row 존재를
  // 전제해 이미 다른 트랜잭션이 지운 토큰이면 ObjectOptimisticLockingFailureException을 던져 호출자
  // 트랜잭션 전체(NotificationHistory 저장 포함)를 롤백시켰다 — 벌크 쿼리는 대상이 0건이어도 조용히 통과
  @Modifying
  @Query("DELETE FROM UserDeviceToken t WHERE t.token IN :tokens")
  void deleteByTokenIn(@Param("tokens") Collection<String> tokens);

  // 알림 발송 대상 유저들의 (userId, token) — FCM data에 유저별 알림 이력 id를 매칭하기 위해 토큰 소유자와 함께 조회
  @Query("SELECT t.user.id AS userId, t.token AS token FROM UserDeviceToken t WHERE t.user.id IN :userIds")
  List<UserTokenView> findUserIdAndTokenByUserIdIn(@Param("userIds") Collection<UUID> userIds);

  // userId·token 프로젝션 — 알림 이력 id를 토큰별 FCM data에 매핑할 때 사용
  interface UserTokenView {
    UUID getUserId();

    String getToken();
  }
}
