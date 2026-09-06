package com.tripfit.tripfit.trip.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import java.util.List;
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

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  private RecommendationEngine engine;

  private User yoonji;

  private User eunseo;

  @BeforeEach
  void setUp() {
    SchedulePort schedulePort =
        new ScheduleAvailabilityAdapter(regularScheduleRepository, personalScheduleRepository);
    GoogleCalendarPort googleCalendarPort = new GoogleCalendarPortAdapter(googleCalendarService);
    engine = new RecommendationEngine(schedulePort, googleCalendarPort);
    yoonji = user("yoonji");
    eunseo = user("eunseo");
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any())).thenReturn(Map.of());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of());
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

  // 정기 일정(근무)이 IMPOSSIBLE인 날 개별 일정으로 오전만 override해 참석 가능하게 만들면 반차(0.5일) 필요
  @Test
  void classifyMembers_halfDayOverrideOnWorkday_needsHalfDayVacation() {
    LocalDate date = LocalDate.now().plusDays(9);
    LocalDate end = date;
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            null,
            null,
            null,
            null);
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
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            1,
            null,
            false,
            null);
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
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            1,
            null,
            false,
            null);
    ReflectionTestUtils.setField(work, "createdAt", LocalDateTime.now());
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(List.of(work));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    List<MemberAttendanceDetail> details =
        engine.classifyMembers(tuesday, wednesday, List.of(member(yoonji)));

    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND);
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
  }

  // 반차 불가 사용자의 종일 연차 대체 — 반나절(오전)만 근무와 겹쳐도 halfVacationAvailable=false면 반차(0.5일)가
  // 아니라 종일 연차 1.0일로 계산돼야 한다(#105 특수 규칙 8)
  @Test
  void classifyMembers_halfVacationUnavailable_singleHalfBlockCostsFullDay() {
    LocalDate date = LocalDate.now().plusDays(9);
    RegularSchedule morningOnlyWork =
        RegularSchedule.create(
            yoonji,
            "오전 근무",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            1,
            null,
            false,
            null);
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

  // 개별 일정으로 이미 막아둔 슬롯은 연차로 전환 불가 — 오전은 개인 일정(결혼식 등)으로 명시적 불가, 오후는
  // 근무만 막고 있어 전환 가능. 연차 예산이 충분해도 오전은 그대로 불가로 남아야 한다(#105 특수 규칙 7)
  @Test
  void classifyMembers_personalScheduleBlocksVacationConversion() {
    LocalDate date = LocalDate.now().plusDays(9);
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            null,
            true,
            null);
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
    RegularSchedule work =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            null,
            true,
            null);
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
    RegularSchedule firstRegistered =
        RegularSchedule.create(
            yoonji,
            "출근",
            allDaysOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            0,
            null,
            false,
            null);
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
            LocalTime.of(18, 0),
            5,
            null,
            true,
            null);
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
            LocalTime.of(23, 59),
            null,
            null,
            null,
            null);
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
}
