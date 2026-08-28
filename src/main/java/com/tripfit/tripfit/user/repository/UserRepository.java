package com.tripfit.tripfit.user.repository;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);

  List<User> findByIsGoogleCalendarConnectedTrue();

  @Query("SELECT u.id FROM User u WHERE u.notificationEnabled = true AND u.deletedAt IS NULL")
  List<UUID> findIdsForScheduleReminder();
}
