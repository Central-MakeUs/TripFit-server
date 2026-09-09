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
import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarPortAdapter;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityAdapter;
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

  // 공휴일 반영 테스트만 이 집합에 날짜를 넣는다 — 비워두면 기존 시나리오는 공휴일 없는 상태로 동작
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
    SchedulePort schedulePort =
        new ScheduleAvailabilityAdapter(
            regularScheduleRepository, personalScheduleRepository, userRepository,
            holidayProvider);
    GoogleCalendarPort googleCalendarPort = new GoogleCalendarPortAdapter(googleCalendarService);
    engine = new RecommendationEngine(schedulePort, googleCalendarPort, holidayProvider);
    yoonji = user("yoonji");
    eunseo = user("eunseo");
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any())).thenReturn(Map.of());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of());
    when(userRepository.findAllById(any())).thenReturn(List.of(yoonji, eunseo));
  }

  // scoring_draft.md 예시: 10일 오전·12일 오후만 불가능 → 연속 6슬롯(⌈9*0.5⌉=5 이상) → 부분 참석
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

  // scoring_draft.md 예시: 10일 오후·12일 오전 불가능 → 최장 연속 4슬롯(<5) → 불참(분리된 구간 합산 금지)
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
    // 완전 불참자는 연차 계산에서 제외 — 참석 구간이 없으니 필요 연차도 0
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

  // 근무(09~18시)가 막는 날을 개별 일정으로 하루 통째 가능하게 override해두면, 그 근무는 오전·오후를 둘 다
  // 걸치므로 종일 연차 1.0일로 집계된다 — 수동 override분과 자동 전환분이 같은 환산식을 쓰는지 확인
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

  // p1.md AS-IS/TO-BE 예시 — 월~금 09:00~18:00 근무, 연차 1일, 금~일 2박3일 여행. 금요일 근무와 겹치지만
  // 연차 1일로 전체 참석 가능해야 한다(#105)
  @Test
  void classifyMembers_p1ExampleFridayOverlap_fullAttendWithOneVacationDay() {
    LocalDate friday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    LocalDate sunday = friday.plusDays(2);
    yoonji.applyVacationPolicy(1, null, false, null);
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

  // 연차 예산 부족 시 강등 — 근무 겹침 2일(화~수)인데 연차 1일만 가능하면 전체 참석이 아니라 부분 참석으로
  // 강등돼야 한다(#105, 무제한 연차 소모 회귀 방지)
  @Test
  void classifyMembers_vacationBudgetInsufficient_degradesToPartialAttend() {
    LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    LocalDate wednesday = tuesday.plusDays(1);
    yoonji.applyVacationPolicy(1, null, false, null);
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

  // 위 테스트의 대칭 케이스 — 근무 겹침 2일에 연차도 2일이면 전체 참석이 된다(기획 리포트 §3 "겹치는 일수 2일 /
  // 연차 2일 → 전체 참석" 행). 예산이 딱 맞을 때 남김없이 다 쓰는지 확인
  @Test
  void classifyMembers_vacationBudgetExactlyCoversOverlap_fullAttend() {
    LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    LocalDate wednesday = tuesday.plusDays(1);
    yoonji.applyVacationPolicy(2, null, false, null);
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

  // 반차 불가 사용자의 종일 연차 대체 — 반나절(오전)만 근무와 겹쳐도 halfVacationAvailable=false면 반차(0.5일)가
  // 아니라 종일 연차 1.0일로 계산돼야 한다(#105 특수 규칙 8)
  @Test
  void classifyMembers_halfVacationUnavailable_singleHalfBlockCostsFullDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, false, null);
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

  // 예산은 정수 일수로 들어오지만 내부적으로 반나절 단위로 쪼개 쓴다 — 반차 가능 사용자가 연차 2일을 가지고
  // 종일 연차 1일(월) + 오전 반차 0.5일(화) = 1.5일을 쓰는 조합을 실제로 고르는지 확인(기획자 문의 사례)
  @Test
  void classifyMembers_halfVacationAvailable_spendsFractionalDaysFromIntegerBudget() {
    LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    LocalDate tuesday = monday.plusDays(1);
    yoonji.applyVacationPolicy(2, null, true, null);
    RegularSchedule mondayWork =
        RegularSchedule.create(
            yoonji,
            "월요일 종일 근무",
            "MON",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    ReflectionTestUtils.setField(mondayWork, "createdAt", LocalDateTime.now().minusDays(1));
    yoonji.applyVacationPolicy(2, null, true, null);
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

    // 월 1.0 + 화 0.5 = 1.5일 — 예산 2일을 다 쓰지 않고 0.5 단위로 정확히 필요한 만큼만 사용
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.5);
  }

  // 위 테스트의 대칭 케이스 — 저녁을 안 막는 근무의 반나절(오전)만 걸리고 halfVacationAvailable=true면
  // 종일 연차가 아니라 오전 반차 0.5일만 든다(근무 단위 전환이 반차 단위를 없애버리지 않았는지 확인)
  @Test
  void classifyMembers_halfVacationAvailable_singleHalfBlockCostsHalfDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, null);
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

  // 개별 일정으로 이미 막아둔 슬롯은 연차로 전환 불가 — 오전은 개인 일정(결혼식 등)으로 명시적 불가, 오후는
  // 근무만 막고 있어 전환 가능. 연차 예산이 충분해도 오전은 그대로 불가로 남아야 한다(#105 특수 규칙 7)
  @Test
  void classifyMembers_personalScheduleBlocksVacationConversion() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, true, null);
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

    // 오후만 연차로 전환되어 오후+저녁만 연속 참석 — 오전까지 연차로 뚫었다면 FULL_ATTEND이었을 것
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0.5);
  }

  // 구글 캘린더 busy로 막힌 슬롯도 연차로 전환 불가 — 오후가 구글 일정으로 busy면 연차 예산이 충분해도
  // 그대로 불가로 남아야 한다(#105 특수 규칙 7)
  @Test
  void classifyMembers_googleBusyBlocksVacationConversion() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, true, null);
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

    // 오전만 연차로 전환되어 홀로 남고(저녁과 연속되지 않음) 오후는 구글 busy로 여전히 불가 → 최장 연속 1
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.NON_ATTEND);
  }

  // 정기 일정을 2개 이상 등록해 연차 값이 다르면, 가장 먼저 등록된(createdAt 오름차순) 행을 예산으로 쓴다(#105)
  @Test
  void classifyMembers_multipleRegularSchedules_usesEarliestCreatedAsBudget() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(0, null, false, null);
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
    // laterRegistered의 연차 정책은 더 이상 적용되지 않는다 — User 단일 값으로 이동하면서 "먼저 등록된 행이 이김"(policySource) 의미가 사라졌기 때문 — firstRegistered의 (0, null, false, null)가 그대로 yoonji의 정책이 된다
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

    // firstRegistered의 연차 0일이 예산으로 쓰여 전환 자체가 불가 — laterRegistered의 5일이 쓰였다면 FULL_ATTEND였을 것
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.NON_ATTEND);
  }

  // 근무가 2개면(예: 낮 근무 + 저녁 알바) 연차도 근무마다 따로 써야 한다 — 낮 근무를 빼도 저녁 알바는
  // 별개 근무라 그대로 막히고, 예산이 1일뿐이면 둘 다 빼지 못해 더 긴 구간이 나오는 낮 근무만 뺀다
  @Test
  void classifyMembers_twoSeparateRegularSchedules_doesNotAutoOpenUnrelatedEveningJob() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, false, null);
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
    yoonji.applyVacationPolicy(1, null, false, null);
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

    // 낮 근무(연차 1일)만 전환되고 저녁 알바는 그대로 막혀있어 전체 참석이 아니다
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  // 위와 같은 낮 근무+저녁 알바 조합이라도 예산이 2일이면 두 근무를 각각 1일씩 빼서 하루를 통째로 비운다 —
  // 근무마다 1일이므로 합계 2.0일
  @Test
  void classifyMembers_twoSeparateRegularSchedules_buysBothWithSufficientBudget() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, false, null);
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
    yoonji.applyVacationPolicy(2, null, false, null);
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

    // 낮 근무 종일 연차(1.0) + 저녁 알바 종일 연차(1.0) = 2.0일 써서 하루 전체 참석
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(2.0);
  }

  // 고정 근무가 저녁에만 있는 사용자(오전·오후는 원래 자유) — "저녁 반차"라는 상품은 없지만, 종일 연차
  // 가격(1.0일)으로는 살 수 있다(#105 후속 — "저녁만 근무하면 연차를 아예 못 쓴다"는 오분류 방지)
  @Test
  void classifyMembers_eveningOnlyRegularSchedule_buysWithFullDayVacation() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, null);
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

    // 반차 가능 여부와 무관하게 저녁은 항상 종일 연차 가격(1.0)으로만 구매 가능
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  // 오후+저녁 근무(13~23시)가 이틀 연속인데 예산이 1일뿐이면, 하루치 근무만 통째로 빼고 나머지 하루는
  // 그대로 막힌다 — 근무 하나를 빼는 값은 항상 1일이므로 예산 1일로는 이틀을 감당하지 못한다(#105 후속 amend)
  @Test
  void classifyMembers_afternoonEveningShiftTwoDays_budgetOneDay_clearsOnlyOneShift() {
    LocalDate start = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, null);
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

    // 첫날 근무를 통째로 빼면 첫날 3슬롯 + 둘째날 오전까지 4슬롯 연속(⌈6*0.5⌉=3 이상) → 부분 참석
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  // 오후+저녁 근무(13~23시)에서 반차 0.5일만 쓰게 되는 유일한 조건 — 저녁이 근무가 아닌 이유(구글 busy 등)로도
  // 막혀 있어 애초에 연차로 열 수 없을 때. 이때는 종일 연차를 사도 저녁이 안 열리므로 오후 반차 0.5일이 최선이다.
  // (예산이 0.5일뿐인 상황은 maxVacationDays가 정수 일수라 발생하지 않는다 — 최소 예산이 이미 1일)
  @Test
  void classifyMembers_afternoonEveningShift_eveningBlockedElsewhere_buysOnlyAfternoonHalf() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(5, null, true, null);
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

    // 저녁은 구글 일정으로도 막혀 연차 대상이 아님 → 오전+오후 2슬롯만 열려 부분 참석(⌈3*0.5⌉=2), 연차 0.5일
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(0.5);
  }

  // 오후+저녁이 이어지는 근무(13~23시)는 종일 연차 1일이면 그 근무 사이클 전체(오후+저녁)가 한 번에 열린다 —
  // 저녁 몫을 따로 사지 않는다("연차 = 근무 하나를 통째로 뺀다", #105 후속 amend)
  @Test
  void classifyMembers_afternoonEveningShift_fullDayVacationOpensWholeShift() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, null);
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

  // 반차 불가 사용자도 같은 근무(13~23시)를 종일 연차 1일로 통째로 뺀다 — 반차를 못 쓴다고 해서 같은 근무의
  // 저녁분을 또 사야 하는 게 아니다(#105 특수 규칙 8과 근무 단위 전환의 상호작용)
  @Test
  void classifyMembers_afternoonEveningShift_halfVacationUnavailable_stillCostsOneDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(2, null, false, null);
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

    // 예산이 2일 남아있어도 근무는 하나뿐이라 1일만 쓴다
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.FULL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  // 오전부터 저녁까지 이어지는 종일 근무(예: 10~20시)도 종일 연차 1일로 오전·오후·저녁이 한 번에 열린다
  @Test
  void classifyMembers_fullDayIntoEveningShift_fullDayVacationOpensWholeShift() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(1, null, true, null);
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

  // 저녁이 근무(19~23시)로 막혀 있어도 동시에 개인 일정(예: 밤 결혼식)으로도 막혀 있으면, 연차 예산이
  // 충분해도 저녁 단독 구매 후보에서 제외돼 열 수 없다 — 연차는 근무만 대체(개인 일정 override가 항상 우선)
  @Test
  void classifyMembers_eveningBlockedByPersonalSchedule_cannotBeOpenedEvenWithBudget() {
    LocalDate date = LocalDate.now().plusDays(9);
    yoonji.applyVacationPolicy(5, null, true, null);
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

    // 오전·오후는 원래 자유라 그 둘만으로 부분 참석 기준(⌈3*0.5⌉=2)을 충족 — 저녁은 연차로도 못 없애 0일 그대로
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isZero();
  }

  // ALL_ATTEND는 하드 필터가 아니다 — 목표 인원 미달(항상 불참자 존재)이어도 후보가 제외되지 않고 TOP3가 그대로 나옴
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

    // 4일 범위·2일 후보 → 슬라이딩 윈도우 3개, eunseo가 항상 불참이어도 전부 후보로 남는다
    assertThat(candidates).hasSize(3);
  }

  // 동점 처리 — 점수가 같으면 불확실 일정 수가 적은 후보가 먼저(시작일이 더 늦어도 우선)
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
    // 두 후보 모두 참석·연차 조건은 동일(점수 동률) — 불확실 일정 없는 8/2가 8/1보다 먼저 와야 함
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

  // 공휴일에 쉬는 사용자(holidayRest=true)는 그날 근무가 없는 것으로 봐야 한다 — 연차를 쓰지 않고 전체 참석
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

  // 같은 근무라도 공휴일이 아니면 종전대로 연차가 필요하다 — 위 테스트의 회귀 방지 짝
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

  // 공휴일에 일하는 사용자(holidayRest=false)는 공휴일에도 종전대로 연차가 필요하다
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
