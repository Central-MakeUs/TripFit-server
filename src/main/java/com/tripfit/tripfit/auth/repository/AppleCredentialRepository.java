package com.tripfit.tripfit.auth.repository;

import com.tripfit.tripfit.auth.domain.AppleCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppleCredentialRepository extends JpaRepository<AppleCredential, UUID> {

  Optional<AppleCredential> findByUser_Id(UUID userId);

  void deleteByUser_Id(UUID userId);
}
