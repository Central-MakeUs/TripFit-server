package com.tripfit.tripfit.user.repository;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);

  List<User> findByIsGoogleCalendarConnectedTrue();

  @Query("SELECT u.id FROM User u WHERE u.notificationEnabled = true AND u.deletedAt IS NULL")
  List<UUID> findIdsForScheduleReminder();

  // 사용자 행에 배타 잠금을 걸고 읽는다. 탈퇴 트랜잭션이 같은 행을 고치는 중이면 그 커밋을 기다린 뒤 최신 값을 읽는다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM User u WHERE u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
