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

  @Modifying
  @Query(
      value = """
          INSERT INTO user_device_token (id, user_id, token, device_type, created_at, updated_at)
          VALUES (:id, :userId, :token, :deviceType, NOW(6), NOW(6))
          ON DUPLICATE KEY UPDATE user_id = :userId, device_type = :deviceType, updated_at = NOW(6)
          """,
      nativeQuery = true)
  void upsertToken(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("token") String token,
      @Param("deviceType") String deviceType);

  @Modifying
  @Query("DELETE FROM UserDeviceToken t WHERE t.token = :token AND t.user.id = :userId")
  long deleteByTokenAndUser_Id(@Param("token") String token, @Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM UserDeviceToken t WHERE t.token IN :tokens")
  void deleteByTokenIn(@Param("tokens") Collection<String> tokens);

  @Query("SELECT t.user.id AS userId, t.token AS token FROM UserDeviceToken t WHERE t.user.id IN :userIds")
  List<UserTokenView> findUserIdAndTokenByUserIdIn(@Param("userIds") Collection<UUID> userIds);

  interface UserTokenView {
    UUID getUserId();

    String getToken();
  }
}
