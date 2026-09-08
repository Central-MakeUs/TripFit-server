package com.tripfit.tripfit.trip.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// new_problem/test_set.md 5인 시나리오 재현 — RecommendationEngine이 문서의 "TripFit 추천 1순위"(10/24~10/26)와
// "운영자용 정답" 참여자별 분류를 그대로 재현하는지 확인한다(#105 저녁 자동 해제 회귀 방지).
@ExtendWith(MockitoExtension.class)
class RecommendationEngineTestSetScenarioTest {

  private final HolidayProvider holidayProvider = (start, end) -> Set.of();

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  private RecommendationEngine engine;

  @BeforeEach
  void setUp() {
    SchedulePort schedulePort =
        new ScheduleAvailabilityAdapter(
            regularScheduleRepository, personalScheduleRepository, holidayProvider);
    GoogleCalendarPort googleCalendarPort = new GoogleCalendarPortAdapter(googleCalendarService);
    engine = new RecommendationEngine(schedulePort, googleCalendarPort, holidayProvider);
    when(googleCalendarService.findBusyDaysByUserIds(any(), any(), any())).thenReturn(Map.of());
  }

  @Test
  void generate_testSetScenario_ranksOct24to26FirstWithOperatorAnswerClassification() {
    User yoonji = user("yoonji"); // A - 평일 직장인
    User eunseo = user("eunseo"); // B - 평일 직장인
    User giyeon = user("giyeon"); // C - 주말 근무 직장인
    User soeun = user("soeun"); // D - 주말 근무 + 공휴일 근무 직장인
    User chaeyeon = user("chaeyeon"); // E - 프리랜서

    List<RegularSchedule> regulars = new ArrayList<>();
    regulars.add(regularSchedule(yoonji, "MON,TUE,WED,THU,FRI", 9, 18, 2, true));
    regulars.add(regularSchedule(eunseo, "MON,TUE,WED,THU,FRI", 10, 19, 1, false));
    regulars.add(regularSchedule(giyeon, "THU,FRI,SAT,SUN", 9, 18, 2, true));
    regulars.add(regularSchedule(soeun, "TUE,WED,THU,FRI,SAT", 12, 20, 1, true));
    // chaeyeon(E)은 고정 근무 없음 — regular 없음
    when(regularScheduleRepository.findByUserIdIn(any())).thenReturn(regulars);

    List<PersonalSchedule> personals = new ArrayList<>();
    // A - 이윤지
    personals.add(personal(yoonji, ld(10, 3), null, ScheduleStatus.IMPOSSIBLE, null, false));
    personals.add(personal(yoonji, ld(10, 26), null, null, ScheduleStatus.IMPOSSIBLE, false));
    personals.add(personal(yoonji, ld(10, 31), null, null, ScheduleStatus.IMPOSSIBLE, false));
    // B - 최은서
    personals.add(personal(eunseo, ld(10, 11), null, null, ScheduleStatus.IMPOSSIBLE, false));
    personals.add(personal(eunseo, ld(10, 16), null, null, null, true));
    personals.add(personal(eunseo, ld(10, 31), ScheduleStatus.IMPOSSIBLE, null, null, false));
    // C - 방기연
    personals.add(
        personal(
            giyeon,
            ld(10, 4),
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            false));
    personals.add(personal(giyeon, ld(10, 27), null, null, null, true));
    personals.add(personal(giyeon, ld(10, 28), null, null, ScheduleStatus.IMPOSSIBLE, false));
    // D - 김소은
    personals.add(personal(soeun, ld(10, 12), null, ScheduleStatus.IMPOSSIBLE, null, false));
    personals.add(personal(soeun, ld(10, 18), null, null, ScheduleStatus.IMPOSSIBLE, false));
    // E - 손채연
    personals.add(
        personal(
            chaeyeon,
            ld(10, 4),
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            null,
            false));
    personals.add(
        personal(
            chaeyeon,
            ld(10, 10),
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            false));
    personals.add(
        personal(
            chaeyeon,
            ld(10, 19),
            null,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.IMPOSSIBLE,
            false));
    personals.add(personal(chaeyeon, ld(10, 20), ScheduleStatus.IMPOSSIBLE, null, null, false));
    personals.add(
        personal(chaeyeon, ld(10, 30), null, null, null, true));
    when(personalScheduleRepository.findByUserIdInAndScheduleDateBetween(any(), any(), any()))
        .thenReturn(personals);

    Trip trip =
        new Trip(
            yoonji, "부스 테스트", ld(10, 1), ld(10, 31), 2, 3, 5, "TESTBOOTH", TripStatus.ONGOING);

    List<TripMember> members =
        List.of(
            member(yoonji, trip),
            member(eunseo, trip),
            member(giyeon, trip),
            member(soeun, trip),
            member(chaeyeon, trip));

    List<RecommendationCandidate> candidates =
        engine.generate(trip, RecommendationMode.BASIC, members);

    // test_set.md "TripFit 추천 1순위" — 10/24(토)~10/26(월)
    assertThat(candidates.get(0).startDate()).isEqualTo(ld(10, 24));
    assertThat(candidates.get(0).endDate()).isEqualTo(ld(10, 26));

    // test_set.md "운영자용 정답" 표 — 참여자별 참석 결과·연차 일수 그대로 재현
    List<MemberAttendanceDetail> details = engine.classifyMembers(ld(10, 24), ld(10, 26), members);
    assertThat(details.get(0).attendance()).isEqualTo(AttendanceType.PARTIAL_ATTEND); // A(윤지) 조기귀가
    assertThat(details.get(0).vacationDays()).isEqualTo(1.0);
    assertThat(details.get(1).attendance()).isEqualTo(AttendanceType.FULL_ATTEND); // B(은서)
    assertThat(details.get(1).vacationDays()).isEqualTo(1.0);
    assertThat(details.get(2).attendance()).isEqualTo(AttendanceType.FULL_ATTEND); // C(기연)
    assertThat(details.get(2).vacationDays()).isEqualTo(2.0);
    assertThat(details.get(3).attendance()).isEqualTo(AttendanceType.FULL_ATTEND); // D(소은)
    assertThat(details.get(3).vacationDays()).isEqualTo(1.0);
    assertThat(details.get(4).attendance()).isEqualTo(AttendanceType.FULL_ATTEND); // E(채연)
    assertThat(details.get(4).vacationDays()).isEqualTo(0.0);
  }

  private static User user(String socialId) {
    User user = new User(socialId, SocialProvider.GOOGLE, null, socialId, null);
    user.setId(java.util.UUID.randomUUID());
    return user;
  }

  private static TripMember member(User user, Trip trip) {
    return new TripMember(
        trip, user, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE, LocalDateTime.now());
  }

  private static LocalDate ld(int month, int day) {
    return LocalDate.of(2026, month, day);
  }

  private static RegularSchedule regularSchedule(
      User user,
      String daysOfWeek,
      int startHour,
      int endHour,
      int maxVacationDays,
      boolean halfVacationAvailable) {
    RegularSchedule schedule =
        RegularSchedule.create(
            user,
            "근무",
            daysOfWeek,
            LocalTime.of(startHour, 0),
            LocalTime.of(endHour, 0),
            maxVacationDays,
            null,
            halfVacationAvailable,
            null);
    ReflectionTestUtils.setField(schedule, "createdAt", LocalDateTime.now());
    return schedule;
  }

  private static PersonalSchedule personal(
      User user,
      LocalDate date,
      ScheduleStatus morning,
      ScheduleStatus afternoon,
      ScheduleStatus evening,
      boolean uncertain) {
    return PersonalSchedule.create(user, date, morning, afternoon, evening, uncertain);
  }
}
