package com.tripfit.tripfit.user.schedule.repository;

import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalScheduleRepository extends JpaRepository<PersonalSchedule, UUID> {

  List<PersonalSchedule> findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate);

  List<PersonalSchedule> findByUserIdInAndScheduleDateBetween(
      Collection<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate);

  Optional<PersonalSchedule> findByUserIdAndScheduleDate(UUID userId, LocalDate scheduleDate);

  // hasPreSchedule 파생용 — personal_schedule row ≥1
  boolean existsByUserId(UUID userId);

  void deleteByUserIdAndScheduleDateIn(UUID userId, Collection<LocalDate> scheduleDates);
}
