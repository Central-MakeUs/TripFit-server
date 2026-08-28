package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleAvailabilityService {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserRepository userRepository;

  private final HolidayProvider holidayProvider;

  private final GoogleCalendarService googleCalendarService;

  public Map<UUID, List<RegularSchedule>> findRegularSchedulesByUserIds(List<UUID> userIds) {
    return regularScheduleRepository.findByUserIdIn(userIds).stream()
        .collect(Collectors.groupingBy(regular -> regular.getUser().getId()));
  }

  public Map<UUID, List<PersonalSchedule>> findPersonalSchedulesByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    return personalScheduleRepository
        .findByUserIdInAndScheduleDateBetween(userIds, startDate, endDate).stream()
        .collect(Collectors.groupingBy(personal -> personal.getUser().getId()));
  }

  public ScheduleAvailability resolveAvailability(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser =
        googleCalendarService.findBusyDaysByUserIds(userIds, startDate, endDate);
    Map<UUID, List<CalendarDayResponse>> mergedByUser =
        resolveMergedSchedules(userIds, startDate, endDate, googleBusyByUser);
    return new ScheduleAvailability(googleBusyByUser, mergedByUser);
  }

  private Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser) {
    Map<UUID, List<RegularSchedule>> regularsByUser = findRegularSchedulesByUserIds(userIds);
    Map<UUID, List<PersonalSchedule>> personalsByUser =
        findPersonalSchedulesByUserIds(userIds, startDate, endDate);

    Map<UUID, User> usersByUser =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    Set<LocalDate> holidays = holidayProvider.findHolidaysBetween(startDate, endDate);

    Map<UUID, List<CalendarDayResponse>> byUser = new HashMap<>();
    for (UUID userId : userIds) {
      User user = usersByUser.get(userId);
      byUser.put(
          userId,
          ScheduleCalendarResolver.resolve(
              regularsByUser.getOrDefault(userId, List.of()),
              personalsByUser.getOrDefault(userId, List.of()),
              startDate,
              endDate,
              googleBusyByUser.getOrDefault(userId, Map.of()),
              holidays,
              user != null && user.isHolidayRest()));
    }
    return byUser;
  }

  public record ScheduleAvailability(
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser,
      Map<UUID, List<CalendarDayResponse>> mergedByUser
  ) {
  }
}
