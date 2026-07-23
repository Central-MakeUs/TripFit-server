package com.tripfit.tripfit.user.repository;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);

  // Google Calendar 30분 폴링 대상 — is_google_calendar_connected=true
  List<User> findByIsGoogleCalendarConnectedTrue();
}
