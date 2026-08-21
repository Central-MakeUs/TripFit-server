package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.auth.domain.GoogleLoginCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleLoginCredentialRepository
    extends JpaRepository<GoogleLoginCredential, UUID> {

  Optional<GoogleLoginCredential> findByUser_Id(UUID userId);

  void deleteByUser_Id(UUID userId);
}
