package com.tripfit.tripfit.user.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleCalendarResolverTest {

  private final User user = new User("social-1", SocialProvider.GOOGLE, "a@b.com", "닉", null);

  @Test
  void resolve_weekendOmitted_weekdaysFromRegular() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(work),
            List.of(),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 7),
            Map.of(),
            Set.of(),
            true);

    Map<LocalDate, CalendarDayResponse> byDate =
        days.stream().collect(Collectors.toMap(CalendarDayResponse::date, Function.identity()));

    assertThat(byDate).doesNotContainKeys(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
    assertThat(byDate.get(LocalDate.of(2026, 8, 3)).morningStatus())
        .isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(byDate.get(LocalDate.of(2026, 8, 3)).eveningStatus())
        .isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(byDate.get(LocalDate.of(2026, 8, 3)).uncertain()).isFalse();
  }

  @Test
  void resolve_personalFullOverride_allThreeSlotsExplicit() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    LocalDate tuesday = LocalDate.of(2026, 8, 4);
    PersonalSchedule personal =
        PersonalSchedule.create(
            user,
            tuesday,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(work),
            List.of(personal),
            tuesday,
            tuesday,
            Map.of(),
            Set.of(),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_personalPartialOverride_untouchedSlotKeepsRegularValue() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    LocalDate thursday = LocalDate.of(2026, 8, 6);

    PersonalSchedule personal =
        PersonalSchedule.create(user, thursday, null, ScheduleStatus.POSSIBLE, null, false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver
            .resolve(
                List.of(work),
                List.of(personal),
                thursday,
                thursday,
                Map.of(),
                Set.of(),
                true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_personalUncertainOnly_slotsFollowRegularButUncertainTrue() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    LocalDate thursday = LocalDate.of(2026, 8, 6);
    PersonalSchedule personal =
        PersonalSchedule.create(user, thursday, null, null, null, true);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver
            .resolve(
                List.of(work),
                List.of(personal),
                thursday,
                thursday,
                Map.of(),
                Set.of(),
                true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().uncertain()).isTrue();
  }

  @Test
  void resolve_personalSlotStatusesNullFromHibernate_fallsBackToRegularInsteadOfNpe() {

    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    LocalDate thursday = LocalDate.of(2026, 8, 6);
    PersonalSchedule personal =
        PersonalSchedule.create(user, thursday, null, null, null, true);
    ReflectionTestUtils.setField(personal, "slotStatuses", null);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver
            .resolve(
                List.of(work),
                List.of(personal),
                thursday,
                thursday,
                Map.of(),
                Set.of(),
                true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().uncertain()).isTrue();
  }

  @Test
  void resolve_multipleRegularsSameWeekday_impossibleWins() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "WED",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    RegularSchedule classAtNight =
        RegularSchedule.create(
            user,
            "수업",
            "WED",
            LocalTime.of(18, 0),
            LocalTime.of(21, 0));

    LocalDate wed = LocalDate.of(2026, 8, 5);
    assertThat(wed.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(work, classAtNight),
            List.of(),
            wed,
            wed,
            Map.of(),
            Set.of(),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void parseDaysOfWeek_trimsAndIgnoresCase() {
    assertThat(ScheduleCalendarResolver.parseDaysOfWeek(" mon , Tue "))
        .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
  }

  @Test
  void resolve_googleOnlyDay_appearsWithImpossibleSlots() {
    LocalDate date = LocalDate.of(2026, 8, 10);
    var googleBusy =
        com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay.create(
            user,
            date,
            true,
            false,
            false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(),
            List.of(),
            date,
            date,
            Map.of(date, googleBusy),
            Set.of(),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_regularAndGoogleBothBusy_orMergedWhenNoOverride() {
    LocalDate date = LocalDate.of(2026, 8, 11);
    var googleBusy =
        com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay.create(
            user,
            date,
            false,
            true,
            false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(),
            List.of(),
            date,
            date,
            Map.of(date, googleBusy),
            Set.of(),
            true);

    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void resolve_personalOverride_winsOverGoogleBusy() {

    LocalDate date = LocalDate.of(2026, 8, 11);
    PersonalSchedule personal =
        PersonalSchedule.create(user, date, null, ScheduleStatus.POSSIBLE, null, false);
    var googleBusy =
        com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay.create(
            user,
            date,
            false,
            true,
            false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(),
            List.of(personal),
            date,
            date,
            Map.of(date, googleBusy),
            Set.of(),
            true);

    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_regularOnly_noPersonalNoGoogle_sparseOmitted() {
    LocalDate saturday = LocalDate.of(2026, 8, 8);
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver
            .resolve(List.of(work), List.of(), saturday, saturday, Map.of(), Set.of(), true);

    assertThat(days).isEmpty();
  }

  @Test
  void resolve_holidayRestUser_holidayDropsRegularAndOmitsDay() {
    LocalDate holiday = LocalDate.of(2026, 8, 17);
    assertThat(holiday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayWork()),
            List.of(),
            holiday,
            holiday,
            Map.of(),
            Set.of(holiday),
            true);

    assertThat(days).isEmpty();
  }

  @Test
  void resolve_holidayRestUser_nonHolidayWeekdayKeepsRegular() {
    LocalDate weekday = LocalDate.of(2026, 8, 18);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayWork()),
            List.of(),
            weekday,
            weekday,
            Map.of(),
            Set.of(LocalDate.of(2026, 8, 17)),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_holidayRestFalseUser_holidayKeepsRegular() {
    LocalDate holiday = LocalDate.of(2026, 8, 17);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayWork()),
            List.of(),
            holiday,
            holiday,
            Map.of(),
            Set.of(holiday),
            false);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void resolve_holidayRestIsPerUser_bothRegularsDroppedOnHoliday() {
    LocalDate holiday = LocalDate.of(2026, 8, 17);

    RegularSchedule company = weekdayWork();
    ReflectionTestUtils.setField(company, "createdAt", LocalDateTime.now().minusDays(1));
    RegularSchedule partTime =
        RegularSchedule.create(
            user,
            "알바",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(19, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(partTime, "createdAt", LocalDateTime.now());

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(company, partTime),
            List.of(),
            holiday,
            holiday,
            Map.of(),
            Set.of(holiday),
            true);

    assertThat(days).isEmpty();
  }

  @Test
  void resolve_holidayRestUser_representativeRowGovernsEvenIfUnmatchedThatDay() {

    LocalDate saturdayHoliday = LocalDate.of(2026, 8, 15);
    assertThat(saturdayHoliday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);

    RegularSchedule weekdayCompany = weekdayWork();
    ReflectionTestUtils.setField(weekdayCompany, "createdAt", LocalDateTime.now().minusDays(1));
    RegularSchedule saturdayShift =
        RegularSchedule.create(
            user,
            "주말 알바",
            "SAT",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(saturdayShift, "createdAt", LocalDateTime.now());

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayCompany, saturdayShift),
            List.of(),
            saturdayHoliday,
            saturdayHoliday,
            Map.of(),
            Set.of(saturdayHoliday),
            true);

    assertThat(days).isEmpty();
  }

  @Test
  void resolve_holidayRestUser_personalOverrideStillWinsOnHoliday() {
    LocalDate holiday = LocalDate.of(2026, 8, 17);
    PersonalSchedule personal =
        PersonalSchedule.create(
            user,
            holiday,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            false);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayWork()),
            List.of(personal),
            holiday,
            holiday,
            Map.of(),
            Set.of(holiday),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void resolve_holidayRestUser_googleBusyStillBlocksOnHoliday() {
    LocalDate holiday = LocalDate.of(2026, 8, 17);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(weekdayWork()),
            List.of(),
            holiday,
            holiday,
            Map.of(
                holiday,
                com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay.create(
                    user,
                    holiday,
                    true,
                    false,
                    false)),
            Set.of(holiday),
            true);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  private RegularSchedule weekdayWork() {
    return RegularSchedule.create(
        user,
        "출근",
        "MON,TUE,WED,THU,FRI",
        LocalTime.of(9, 0),
        LocalTime.of(18, 0));
  }
}
