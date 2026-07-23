package com.tripfit.tripfit.user.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

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
            LocalTime.of(18, 0),
            2,
            null,
            false,
            true);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(
            List.of(work),
            List.of(),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 7));

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
  void resolve_personalOverridesEntireDay_s1() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            null,
            false,
            true);
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
            tuesday);

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_multipleRegularsSameWeekday_impossibleWins() {
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "WED",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            null,
            false,
            true);
    RegularSchedule classAtNight =
        RegularSchedule.create(
            user,
            "수업",
            "WED",
            LocalTime.of(18, 0),
            LocalTime.of(21, 0),
            2,
            null,
            false,
            true);

    LocalDate wed = LocalDate.of(2026, 8, 5);
    assertThat(wed.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);

    List<CalendarDayResponse> days =
        ScheduleCalendarResolver.resolve(List.of(work, classAtNight), List.of(), wed, wed);

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
            Map.of(date, googleBusy));

    assertThat(days).hasSize(1);
    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void resolve_mergeWithGoogle_orImpossibleWins() {
    LocalDate date = LocalDate.of(2026, 8, 11);
    PersonalSchedule personal =
        PersonalSchedule.create(
            user,
            date,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false);
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
            Map.of(date, googleBusy));

    assertThat(days.getFirst().morningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(days.getFirst().afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }
}
