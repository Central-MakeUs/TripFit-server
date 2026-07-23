package com.tripfit.tripfit.user.googlecalendar.repository;

import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarCredentialRepository
    extends JpaRepository<GoogleCalendarCredential, UUID> {

  Optional<GoogleCalendarCredential> findByUser_Id(UUID userId);

  void deleteByUser_Id(UUID userId);
}
