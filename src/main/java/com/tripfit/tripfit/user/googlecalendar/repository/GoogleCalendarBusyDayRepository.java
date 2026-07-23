package com.tripfit.tripfit.user.googlecalendar.repository;

import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarBusyDayRepository
    extends JpaRepository<GoogleCalendarBusyDay, UUID> {

  List<GoogleCalendarBusyDay> findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate);

  List<GoogleCalendarBusyDay> findByUser_IdInAndScheduleDateBetweenOrderByScheduleDateAsc(
      Collection<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate);

  void deleteByUser_Id(UUID userId);

  void deleteByUser_IdAndScheduleDateBefore(UUID userId, LocalDate scheduleDate);

  void deleteByUser_IdAndScheduleDateAfter(UUID userId, LocalDate scheduleDate);
}
