package com.tripfit.tripfit.trip.membership.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// docs/product/fe-context/schedule-personal-override-scenarios.md 시나리오 10 — 여행방 멤버 달력이
// 본인 캘린더(schedule-slot-override O1.4)와 동일한 병합 결과를 내는지 실제 MySQL(Testcontainers) DB로 검증한다
// (TripMemberQueryService.buildLive → TripServiceSupport.resolveMergedSchedules → 공용
// ScheduleCalendarResolver 재사용 여부를 실제 DB 라운드트립으로 확인)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TripMemberScheduleCalendarIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private TripMemberRepository tripMemberRepository;

  @Autowired
  private RegularScheduleRepository regularScheduleRepository;

  @Autowired
  private PersonalScheduleRepository personalScheduleRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void getScheduleCalendar_reflectsMemberPartialOverride_sameAsPersonalCalendar()
      throws Exception {
    User owner =
        new User("trip-member-sub", SocialProvider.GOOGLE, "owner@example.com", "방장", null);
    owner.applyProfilePatch("길동", "홍", null);
    // 연차·반차·공휴일 휴무는 이제 User 소유 값
    owner.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
    owner = userRepository.save(owner);
    String accessToken = jwtService.createAccessToken(owner.getId());

    LocalDate startRange = LocalDate.now().plusDays(30);
    LocalDate endRange = startRange.plusDays(6);
    LocalDate workday = startRange.plusDays(2);

    // 정기(평일 09~18시) + 그중 하루만 개별 오버라이드(오후만 가능) — 부분 오버라이드가 그룹 달력에도 반영되는지가 핵심
    regularScheduleRepository.save(
        RegularSchedule.create(
            owner,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0)));
    personalScheduleRepository.save(
        PersonalSchedule.create(
            owner,
            workday,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false));

    Trip trip =
        tripRepository.save(
            new Trip(
                owner,
                "여행방 멤버 달력 테스트",
                startRange,
                endRange,
                2,
                3,
                4,
                "MBRCAL",
                TripStatus.ONGOING));
    tripMemberRepository.save(
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now()));

    // 그룹 달력 — 부분 오버라이드(오후만 POSSIBLE)와 정기 계산값(아침 IMPOSSIBLE)이 섞여 반영돼야 한다
    mockMvc
        .perform(
            get("/api/v1/trips/" + trip.getId() + "/members/schedule-calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readOnly").value(false))
        .andExpect(jsonPath("$.data.members[0].userId").value(owner.getId().toString()))
        .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
        .andExpect(jsonPath("$.data.members[0].memberStatus").value("ACTIVE"));

    // 같은 날짜를 본인 캘린더(GET /users/schedule/calendar)로도 조회해 완전히 같은 값인지 대조
    var groupResult =
        mockMvc
            .perform(
                get("/api/v1/trips/" + trip.getId() + "/members/schedule-calendar")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andReturn();
    var personalResult =
        mockMvc
            .perform(
                get("/api/v1/users/schedule/calendar")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .param("startDate", workday.toString())
                    .param("endDate", workday.toString()))
            .andReturn();

    String groupBody = groupResult.getResponse().getContentAsString();
    String personalBody = personalResult.getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(groupBody)
        .contains("\"morningStatus\":\"IMPOSSIBLE\"")
        .contains("\"afternoonStatus\":\"POSSIBLE\"")
        .contains("\"eveningStatus\":\"POSSIBLE\"");
    org.assertj.core.api.Assertions.assertThat(personalBody)
        .contains("\"morningStatus\":\"IMPOSSIBLE\"")
        .contains("\"afternoonStatus\":\"POSSIBLE\"")
        .contains("\"eveningStatus\":\"POSSIBLE\"");
  }
}
