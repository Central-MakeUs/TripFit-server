package com.tripfit.tripfit.trip.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.schedule.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityService;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationEngineTest {

  private final Set<LocalDate> holidays = new HashSet<>();

  private final HolidayProvider holidayProvider = (start, end) -> holidays;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @Mock
  private UserRepository userRepository;

  private RecommendationEngine engine;

  private User yoonji;

  private User eunseo;

  @BeforeEach
  void setUp() {
    ScheduleAvailabilityService scheduleAvailabilityService =
        new ScheduleAvailabilityService(
            regularScheduleRepository, personalScheduleRepository, userRepository,
            holidayProvider, googleCalendarService);
    engine = new RecommendationEngine(scheduleAvailabilityService, holidayProvider);
    yoonji = user("yoonji");
    eunseo = user("eunseo");
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any())).thenReturn(Map.of());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of());
    when(userRepository.findAllById(any())).thenReturn(List.of(yoonji, eunseo));
  }

  @Test
  void classifyMembers_partialAttendBoundaryExample_yoonji() {
    LocalDate start = LocalDate.now().plusDays(9);
    LocalDate end = LocalDate.now().plusDays(11);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(
            List.of(
                personalDay(
                    yoonji,
                    start,
                    ScheduleStatus.IMPOSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false),
                personalDay(
                    yoonji,
                    start.plusDays(1),
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false),
                personalDay(
                    yoonji,
                    end,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.IMPOSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(start, end, List.of(member(yoonji)));

    assertThat(details).hasSize(1);
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
  }

  @Test
  void classifyMembers_nonAttendBoundaryExample_eunseo() {
    LocalDate start = LocalDate.now().plusDays(9);
    LocalDate end = LocalDate.now().plusDays(11);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(
            List.of(
                personalDay(
                    eunseo,
                    start,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.IMPOSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false),
                personalDay(
                    eunseo,
                    start.plusDays(1),
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false),
                personalDay(
                    eunseo,
                    end,
                    ScheduleStatus.IMPOSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(start, end, List.of(member(eunseo)));

    assertThat(details).hasSize(1);
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.NON_ATTEND);

    assertThat(details.get(0).vacationDays()).isZero();
  }

  @Test
  void classifyMembers_noSchedules_fullAttendNoUncertainNoVacation() {
    LocalDate start = LocalDate.now().plusDays(9);
    LocalDate end = LocalDate.now().plusDays(11);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(start, end, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).uncertainDays()).isZero();
    assertThat(details.get(0).vacationDays()).isZero();
  }

  @Test
  void classifyMembers_uncertainDayCounted_regardlessOfAttendance() {
    LocalDate start = LocalDate.now().plusDays(9);
    LocalDate end = LocalDate.now().plusDays(10);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(
            List.of(
                personalDay(
                    yoonji,
                    start,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    true)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(start, end, List.of(member(yoonji)));

    assertThat(details.get(0).uncertainDays()).isEqualTo(1);
  }

  @Test
  void classifyMembers_manualFullDayOverrideOnWorkday_countsOneVacationDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    LocalDate end = date;
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(
        work,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE));
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(
            List.of(
                personalDay(
                    yoonji,
                    date,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    false)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, end, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_p1ExampleFridayOverlap_fullAttendWithOneVacationDay() {
    LocalDate friday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    LocalDate sunday = friday.plusDays(2);
    yoonji.applyVacationPolicy(1, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(friday, sunday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_vacationBudgetInsufficient_degradesToPartialAttend() {
    LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    LocalDate wednesday = tuesday.plusDays(1);
    yoonji.applyVacationPolicy(1, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(tuesday, wednesday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_vacationBudgetExactlyCoversOverlap_fullAttend() {
    LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    LocalDate wednesday = tuesday.plusDays(1);
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(tuesday, wednesday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(2.0);
  }

  @Test
  void classifyMembers_halfVacationUnavailable_singleHalfBlockCostsFullDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, false, true);
    RegularSchedule morningOnlyWork =
        RegularSchedule.create(
            yoonji,
            "오전 근무",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(12, 0));
    ReflectionTestUtils.setField(
        morningOnlyWork,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(morningOnlyWork, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(morningOnlyWork));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_halfVacationAvailable_spendsFractionalDaysFromIntegerBudget() {
    LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    LocalDate tuesday = monday.plusDays(1);
    yoonji.applyVacationPolicy(2, null, true, true);
    RegularSchedule mondayWork =
        RegularSchedule.create(
            yoonji,
            "월요일 종일 근무",
            "MON",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(mondayWork, "createdAt", LocalDateTime.now().minusDays(1));
    yoonji.applyVacationPolicy(2, null, true, true);
    RegularSchedule tuesdayMorningWork =
        RegularSchedule.create(
            yoonji,
            "화요일 오전 근무",
            "TUE",
            LocalTime.of(9, 0),
            LocalTime.of(12, 0));
    ReflectionTestUtils.setField(tuesdayMorningWork, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(mondayWork, tuesdayMorningWork));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(monday, tuesday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.5);
  }

  @Test
  void classifyMembers_halfVacationAvailable_singleHalfBlockCostsHalfDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, true);
    RegularSchedule morningOnlyWork =
        RegularSchedule.create(
            yoonji,
            "오전 근무",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(12, 0));
    ReflectionTestUtils.setField(
        morningOnlyWork,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(morningOnlyWork, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(morningOnlyWork));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0.5);
  }

  @Test
  void classifyMembers_personalScheduleBlocksVacationConversion() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, true, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    PersonalSchedule weddingMorning =
        PersonalSchedule.create(
            yoonji,
            date,
            ScheduleStatus.IMPOSSIBLE,
            null,
            null,
            false);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of(weddingMorning));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0.5);
  }

  @Test
  void classifyMembers_googleBusyBlocksVacationConversion() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, true, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());
    GoogleCalendarBusyDay afternoonBusy =
        GoogleCalendarBusyDay.create(yoonji, date, false, true, false);
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any()))
        .thenReturn(Map.of(yoonji.getId(), Map.of(date, afternoonBusy)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.NON_ATTEND);
  }

  @Test
  void classifyMembers_multipleRegularSchedules_usesSingleUserVacationBudget() {
    LocalDate date = LocalDate.now().plusDays(9);

    yoonji.applyVacationPolicy(0, null, false, true);
    RegularSchedule firstRegistered =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(
        firstRegistered,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(
        firstRegistered,
        "createdAt",
        LocalDateTime.now().minusDays(1));

    RegularSchedule laterRegistered =
        RegularSchedule.create(
            yoonji,
            "부업",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(
        laterRegistered,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(laterRegistered, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(laterRegistered, firstRegistered));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.NON_ATTEND);
  }

  @Test
  void classifyMembers_twoSeparateRegularSchedules_doesNotAutoOpenUnrelatedEveningJob() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, false, true);
    RegularSchedule dayJob =
        RegularSchedule.create(
            yoonji,
            "낮 근무",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(
        dayJob,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(dayJob, "createdAt", LocalDateTime.now().minusDays(1));
    yoonji.applyVacationPolicy(1, null, false, true);
    RegularSchedule eveningJob =
        RegularSchedule.create(
            yoonji,
            "저녁 알바",
            allDaysOfWeek(),
            LocalTime.of(19, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(
        eveningJob,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.POSSIBLE, ScheduleStatus.POSSIBLE,
            ScheduleStatus.IMPOSSIBLE));
    ReflectionTestUtils.setField(eveningJob, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(dayJob, eveningJob));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_twoSeparateRegularSchedules_buysBothWithSufficientBudget() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule dayJob =
        RegularSchedule.create(
            yoonji,
            "낮 근무",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(
        dayJob,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE));
    ReflectionTestUtils.setField(dayJob, "createdAt", LocalDateTime.now().minusDays(1));
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule eveningJob =
        RegularSchedule.create(
            yoonji,
            "저녁 알바",
            allDaysOfWeek(),
            LocalTime.of(19, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(
        eveningJob,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.POSSIBLE, ScheduleStatus.POSSIBLE,
            ScheduleStatus.IMPOSSIBLE));
    ReflectionTestUtils.setField(eveningJob, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(dayJob, eveningJob));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(2.0);
  }

  @Test
  void classifyMembers_eveningOnlyRegularSchedule_buysWithFullDayVacation() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, true);
    RegularSchedule eveningOnlyJob =
        RegularSchedule.create(
            yoonji,
            "저녁 알바",
            allDaysOfWeek(),
            LocalTime.of(19, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(
        eveningOnlyJob,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.POSSIBLE, ScheduleStatus.POSSIBLE,
            ScheduleStatus.IMPOSSIBLE));
    ReflectionTestUtils.setField(eveningOnlyJob, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(eveningOnlyJob));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_afternoonEveningShiftTwoDays_budgetOneDay_clearsOnlyOneShift() {
    LocalDate start = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, true);
    RegularSchedule afternoonIntoEvening =
        RegularSchedule.create(
            yoonji,
            "오후+저녁 근무",
            allDaysOfWeek(),
            LocalTime.of(13, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(afternoonIntoEvening, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(afternoonIntoEvening));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(start, start.plusDays(1), List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_afternoonEveningShift_eveningBlockedElsewhere_buysOnlyAfternoonHalf() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(5, null, true, true);
    RegularSchedule afternoonIntoEvening =
        RegularSchedule.create(
            yoonji,
            "오후+저녁 근무",
            allDaysOfWeek(),
            LocalTime.of(13, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(afternoonIntoEvening, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(afternoonIntoEvening));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());
    GoogleCalendarBusyDay eveningBusy =
        GoogleCalendarBusyDay.create(yoonji, date, false, false, true);
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any()))
        .thenReturn(Map.of(yoonji.getId(), Map.of(date, eveningBusy)));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0.5);
  }

  @Test
  void classifyMembers_afternoonEveningShift_fullDayVacationOpensWholeShift() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, true);
    RegularSchedule afternoonIntoEvening =
        RegularSchedule.create(
            yoonji,
            "오후+저녁 근무",
            allDaysOfWeek(),
            LocalTime.of(13, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(afternoonIntoEvening, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(afternoonIntoEvening));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_afternoonEveningShift_halfVacationUnavailable_stillCostsOneDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule afternoonIntoEvening =
        RegularSchedule.create(
            yoonji,
            "오후+저녁 근무",
            allDaysOfWeek(),
            LocalTime.of(13, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(afternoonIntoEvening, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any()))
        .thenReturn(List.of(afternoonIntoEvening));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_fullDayIntoEveningShift_fullDayVacationOpensWholeShift() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, true);
    RegularSchedule fullDayIntoEvening =
        RegularSchedule.create(
            yoonji,
            "종일 근무",
            allDaysOfWeek(),
            LocalTime.of(10, 0),
            LocalTime.of(20, 0));
    ReflectionTestUtils.setField(fullDayIntoEvening, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(fullDayIntoEvening));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_eveningBlockedByPersonalSchedule_cannotBeOpenedEvenWithBudget() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(5, null, true, true);
    RegularSchedule eveningOnlyJob =
        RegularSchedule.create(
            yoonji,
            "저녁 알바",
            allDaysOfWeek(),
            LocalTime.of(19, 0),
            LocalTime.of(23, 0));
    ReflectionTestUtils.setField(eveningOnlyJob, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(eveningOnlyJob));
    PersonalSchedule nightWedding =
        PersonalSchedule.create(yoonji, date, null, null, ScheduleStatus.IMPOSSIBLE, false);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of(nightWedding));

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(date, date, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isZero();
  }

  @Test
  void generate_allAttendMode_doesNotHardFilterLowAttendanceCandidates() {
    Trip trip =
        trip(RecommendationMode.ALL_ATTEND, LocalDate.now(), LocalDate.now().plusDays(3), 2);
    RegularSchedule alwaysBusy =
        RegularSchedule.create(
            eunseo,
            "항상불가",
            "MON,TUE,WED,THU,FRI,SAT,SUN",
            LocalTime.of(0, 0),
            LocalTime.of(23, 59));
    ReflectionTestUtils.setField(
        alwaysBusy,
        "slotStatuses",
        new SlotStatuses(ScheduleStatus.IMPOSSIBLE, ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE));
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(alwaysBusy));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<TripMember> members = List.of(member(yoonji), member(eunseo));
    List<RecommendationCandidate> candidates =
        engine.generate(trip, RecommendationMode.ALL_ATTEND, members);

    assertThat(candidates).hasSize(3);
  }

  @Test
  void generate_tieBreak_byUncertainScheduleCountBeforeStartDate() {
    LocalDate earlierUncertainDay = LocalDate.now();
    LocalDate laterCleanDay = LocalDate.now().plusDays(1);
    Trip trip = trip(RecommendationMode.BASIC, earlierUncertainDay, laterCleanDay, 1);
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(
            List.of(
                personalDay(
                    yoonji,
                    earlierUncertainDay,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    ScheduleStatus.POSSIBLE,
                    true)));

    List<TripMember> members = List.of(member(yoonji));
    List<RecommendationCandidate> candidates =
        engine.generate(trip, RecommendationMode.BASIC, members);

    assertThat(candidates).hasSize(2);

    assertThat(candidates.get(0).startDate()).isEqualTo(laterCleanDay);
    assertThat(candidates.get(1).startDate()).isEqualTo(earlierUncertainDay);
  }

  private static User user(String socialId) {
    User user = new User(socialId, SocialProvider.GOOGLE, null, socialId, null);
    user.setId(UUID.randomUUID());
    return user;
  }

  private static TripMember member(User user) {
    Trip trip =
        new Trip(
            user, "테스트", LocalDate.now(), LocalDate.now().plusDays(30), 2, 3, 6, "ABC123",
            TripStatus.ONGOING);
    return new TripMember(trip, user, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
        LocalDateTime.now());
  }

  private static Trip trip(
      RecommendationMode mode,
      LocalDate start,
      LocalDate end,
      int durationDays) {
    Trip trip =
        new Trip(
            user("owner"), "테스트", start, end, durationDays - 1, durationDays, 6, "ABC123",
            TripStatus.ONGOING);
    ReflectionTestUtils.setField(trip, "lastRecommendationMode", mode);
    return trip;
  }

  private static String allDaysOfWeek() {
    return "MON,TUE,WED,THU,FRI,SAT,SUN";
  }

  private static PersonalSchedule personalDay(
      User user,
      LocalDate date,
      ScheduleStatus morning,
      ScheduleStatus afternoon,
      ScheduleStatus evening,
      boolean uncertain) {
    return PersonalSchedule.create(user, date, morning, afternoon, evening, uncertain);
  }

  @Test
  void classifyMembers_holidayRestUser_holidayNeedsNoVacation() {
    LocalDate holiday = LocalDate.now().plusDays(9);
    holidays.add(holiday);
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(holiday, holiday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0);
  }

  @Test
  void classifyMembers_holidayRestUser_nonHolidayStillNeedsVacation() {
    LocalDate workday = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(workday, workday, List.of(member(yoonji)));

    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  @Test
  void classifyMembers_holidayRestFalseUser_holidayStillNeedsVacation() {
    LocalDate holiday = LocalDate.now().plusDays(9);
    holidays.add(holiday);
    yoonji.applyVacationPolicy(2, null, false, false);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(holiday, holiday, List.of(member(yoonji)));

    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }
}
