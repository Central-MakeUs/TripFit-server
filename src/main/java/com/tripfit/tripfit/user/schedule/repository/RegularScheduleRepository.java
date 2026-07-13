package com.tripfit.tripfit.user.schedule.repository;

import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularScheduleRepository extends JpaRepository<RegularSchedule, UUID> {

  List<RegularSchedule> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<RegularSchedule> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserId(UUID userId);
}
