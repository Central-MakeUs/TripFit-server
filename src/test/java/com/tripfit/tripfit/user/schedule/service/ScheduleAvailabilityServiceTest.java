package com.tripfit.tripfit.user.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.user.domain.SocialProvider;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleAvailabilityServiceTest {

  private static final UUID USER_WITH_SCHEDULE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID USER_WITHOUT_SCHEDULE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private HolidayProvider holidayProvider;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @InjectMocks
  private ScheduleAvailabilityService scheduleAvailabilityService;

  private User userWithSchedule;

  @BeforeEach
  void setUp() {
    userWithSchedule = user(USER_WITH_SCHEDULE_ID);
  }

  @Test
  void findRegularSchedulesByUserIds_groupsResultsByUserId() {
    User userA = user(USER_WITH_SCHEDULE_ID);
    User userB = user(USER_WITHOUT_SCHEDULE_ID);
    RegularSchedule regularA = regularSchedule(userA);
    RegularSchedule regularB = regularSchedule(userB);
    List<UUID> userIds = List.of(USER_WITH_SCHEDULE_ID, USER_WITHOUT_SCHEDULE_ID);
    when(regularScheduleRepository.findByUserIdIn(userIds))
        .thenReturn(List.of(regularA, regularB));

    Map<UUID, List<RegularSchedule>> grouped =
        scheduleAvailabilityService.findRegularSchedulesByUserIds(userIds);

    assertThat(grouped.get(USER_WITH_SCHEDULE_ID)).containsExactly(regularA);
    assertThat(grouped.get(USER_WITHOUT_SCHEDULE_ID)).containsExactly(regularB);
  }

  @Test
  void findPersonalSchedulesByUserIds_groupsResultsByUserId() {
    LocalDate startDate = DATE;
    LocalDate endDate = DATE.plusDays(1);
    PersonalSchedule personal =
        PersonalSchedule.create(
            userWithSchedule,
            DATE,
            ScheduleStatus.IMPOSSIBLE,
            null,
            null,
            false);
    List<UUID> userIds = List.of(USER_WITH_SCHEDULE_ID);
    when(
        personalScheduleRepository.findByUserIdInAndScheduleDateBetween(
            userIds,
            startDate,
            endDate))
        .thenReturn(List.of(personal));

    Map<UUID, List<PersonalSchedule>> grouped =
        scheduleAvailabilityService.findPersonalSchedulesByUserIds(
            userIds,
            startDate,
            endDate);

    assertThat(grouped.get(USER_WITH_SCHEDULE_ID)).containsExactly(personal);
  }

  @Test
  void resolveAvailability_appliesGoogleBusyWhenNoOtherSchedule() {
    List<UUID> userIds = List.of(USER_WITH_SCHEDULE_ID, USER_WITHOUT_SCHEDULE_ID);
    GoogleCalendarBusyDay busyDay =
        GoogleCalendarBusyDay.create(userWithSchedule, DATE, true, false, false);
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser =
        Map.of(USER_WITH_SCHEDULE_ID, Map.of(DATE, busyDay));
    when(googleCalendarService.findBusyDaysByUserIds(userIds, DATE, DATE))
        .thenReturn(googleBusyByUser);
    when(regularScheduleRepository.findByUserIdIn(userIds)).thenReturn(List.of());
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(userIds, DATE, DATE))
        .thenReturn(List.of());
    when(userRepository.findAllById(userIds)).thenReturn(List.of(userWithSchedule));
    when(holidayProvider.findHolidaysBetween(DATE, DATE)).thenReturn(Set.of());

    ScheduleAvailabilityService.ScheduleAvailability availability =
        scheduleAvailabilityService.resolveAvailability(userIds, DATE, DATE);

    assertThat(availability.googleBusyByUser()).isEqualTo(googleBusyByUser);
    List<CalendarDayResponse> merged = availability.mergedByUser().get(USER_WITH_SCHEDULE_ID);
    assertThat(merged).hasSize(1);
    assertThat(merged.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(merged.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolveAvailability_returnsEmptyListForUserWithNoScheduleOrBusy() {
    List<UUID> userIds = List.of(USER_WITHOUT_SCHEDULE_ID);
    when(googleCalendarService.findBusyDaysByUserIds(userIds, DATE, DATE)).thenReturn(Map.of());
    when(regularScheduleRepository.findByUserIdIn(userIds)).thenReturn(List.of());
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(userIds, DATE, DATE))
        .thenReturn(List.of());
    when(userRepository.findAllById(userIds)).thenReturn(List.of());
    when(holidayProvider.findHolidaysBetween(DATE, DATE)).thenReturn(Set.of());

    ScheduleAvailabilityService.ScheduleAvailability availability =
        scheduleAvailabilityService.resolveAvailability(userIds, DATE, DATE);

    assertThat(availability.mergedByUser().get(USER_WITHOUT_SCHEDULE_ID)).isEmpty();
  }

  private static User user(UUID id) {
    User user =
        new User(
            "social-" + id,
            SocialProvider.GOOGLE,
            "user@example.com",
            "닉네임",
            "https://example.com/profile.png");
    user.setId(id);
    return user;
  }

  private static RegularSchedule regularSchedule(User user) {
    return RegularSchedule.create(
        user,
        "정기 일정",
        "MON",
        LocalTime.of(9, 0),
        LocalTime.of(18, 0));
  }
}
