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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    work.setSlotStatuses(
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
    alwaysBusy.setSlotStatuses(
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
    trip.setLastRecommendationMode(mode);
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
