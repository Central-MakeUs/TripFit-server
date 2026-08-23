package com.tripfit.tripfit.user.schedule.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// docs/product/fe-context/schedule-personal-override-scenarios.md 시나리오 1~18(O1.4)을
// 실제 MySQL(Testcontainers) DB + MockMvc(JWT)로 PATCH/GET 라운드트립 검증한다. 각 @Test는 서로 다른 날짜를 써서
// 실행 순서와 무관하게 독립적으로 통과하도록 설계했다(문서의 "이어지는 시나리오" 서술은
// 관련된 @Test 메서드 하나 안에서 순차 호출로 재현).
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
class PersonalScheduleOverrideIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RegularScheduleRepository regularScheduleRepository;

  @Autowired
  private GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  private MockMvc mockMvc;

  private String accessToken;

  private User user;

  // 기준 주(週) 월요일 — "오늘"과 무관하게 항상 미래인 날짜를 잡아 조회 윈도우 검증에 안 걸리게 함
  private LocalDate mon;

  private LocalDate tue;

  private LocalDate wed;

  private LocalDate thu;

  private LocalDate fri;

  private LocalDate sat;

  private LocalDate sun;

  @BeforeAll
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

    user = new User("google-sub-o14", SocialProvider.GOOGLE, "o14@example.com", "유저A", null);
    user.setFirstName("길동");
    user.setLastName("홍");
    user = userRepository.save(user);
    accessToken = jwtService.createAccessToken(user.getId());

    mon =
        LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    tue = mon.plusDays(1);
    wed = mon.plusDays(2);
    thu = mon.plusDays(3);
    fri = mon.plusDays(4);
    sat = mon.plusDays(5);
    sun = mon.plusDays(6);

    // R1: 평일 09~18시 근무 → 아침·오후 IMPOSSIBLE, 저녁 POSSIBLE
    regularScheduleRepository.save(
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            VacationApplyPeriod.ANY,
            false,
            true));
    // R2: 수요일 저녁 수업 19~21시 → 아침·오후 POSSIBLE, 저녁 IMPOSSIBLE (수요일은 R1과 겹쳐 하루 종일 IMPOSSIBLE)
    regularScheduleRepository.save(
        RegularSchedule.create(
            user,
            "저녁 수업",
            "WED",
            LocalTime.of(19, 0),
            LocalTime.of(21, 0),
            2,
            VacationApplyPeriod.ANY,
            false,
            true));
  }

  private String bearer() {
    return "Bearer " + accessToken;
  }

  // 시나리오 1 — 아무것도 안 건드린 상태의 평일은 정기값 그대로, 수요일만 하루 종일 불가능
  // 시나리오 7 — 정기·개별·구글이 전부 없는 주말은 응답 자체에서 생략(sparse)
  @Test
  void baselineWeek_weekdaysReflectRegularOnly_weekendOmitted() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", mon.toString())
                .param("endDate", fri.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(5))
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[2].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[2].afternoonStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[2].eveningStatus").value("IMPOSSIBLE"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", sat.toString())
                .param("endDate", sun.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(0));
  }

  // 시나리오 2·3·4·5·6을 한 흐름으로 — 부분 오버라이드 → 전체 오버라이드 → uncertain 토글이 슬롯을
  // 절대 안 건드리는지(왕복) → slots+uncertain 동시 반영까지 실제 PATCH/GET으로 검증
  @Test
  void partialOverride_thenFullOverride_thenUncertainRoundtripNeverTouchesSlots_thenCombined()
      throws Exception {
    // 2. 오후 반차 — 슬롯 3개를 명시(오후만 실제로 바뀜, 아침·저녁은 정기값과 같은 값을 재전송)
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                        """
                        .formatted(thu)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").exists())
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].uncertain").value(false));

    // 3. 급한 회식 — 하루 종일 불가능으로 전체 오버라이드(저녁은 정기 POSSIBLE인데도 오버라이드가 이김)
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                        """
                        .formatted(thu)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].eveningStatus").value("IMPOSSIBLE"));

    // 시나리오 2 상태로 되돌아옴(오후만 가능) — 2부(uncertain) 검증의 출발점
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                        """
                        .formatted(thu)))
        .andExpect(status().isOk());

    // 4. uncertain=true만 보냄(slots 키 자체 생략) — 슬롯은 절대 안 건드려야 함
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items": [{"scheduleDate": "%s", "uncertain": true}]}
                    """.formatted(thu)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].uncertain").value(true))
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", thu.toString())
                .param("endDate", thu.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].uncertain").value(true))
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"));

    // 5. uncertain=false로 다시 끔 — 슬롯은 여전히(시나리오 2와) 동일해야 함
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items": [{"scheduleDate": "%s", "uncertain": false}]}
                    """.formatted(thu)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].uncertain").value(false))
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"));

    // 6. slots+uncertain을 한 아이템에 같이 담아 동시 반영
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE"}, "uncertain": true}]}
                        """
                        .formatted(thu)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.items[0].uncertain").value(true));
  }

  // 시나리오 8 — 구글 캘린더로 주말이 채워져도, 개별 오버라이드가 있으면 구글 신호와 무관하게 이긴다
  // 일요일을 쓴다 — 토요일은 아래 regularPatternChange 테스트의 격리 정기 일정(SAT, 주 단위 반복)이
  // DB에 공유되므로, 다른 테스트가 토요일을 같이 쓰면 실행 순서에 따라 오염될 수 있다
  @Test
  void googleCalendarMerge_explicitOverrideWinsOverBusySignal() throws Exception {
    LocalDate googleSun = mon.plusDays(13);
    googleCalendarBusyDayRepository.save(
        GoogleCalendarBusyDay.create(user, googleSun, false, true, false));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", googleSun.toString())
                .param("endDate", googleSun.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                        """
                        .formatted(googleSun)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].afternoonStatus").value("POSSIBLE"));
  }

  // 시나리오 11 — 오버라이드된 날짜는 정기 패턴이 바뀌어도 안 따라가고, 오버라이드 없는 날짜는 새 패턴을 따른다
  // (공유 R1/R2에 영향 없게 이 테스트 전용 정기 일정 하나를 별도로 만들어 사용)
  @Test
  void regularPatternChange_overriddenDateFrozen_plainDateFollowsNewPattern() throws Exception {
    RegularSchedule isolated =
        regularScheduleRepository.save(
            RegularSchedule.create(
                user,
                "격리 테스트용",
                "SAT",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                2,
                VacationApplyPeriod.ANY,
                false,
                true));
    LocalDate overriddenSat = mon.plusDays(26);
    LocalDate plainSat = mon.plusDays(33);

    // 아침만 가능으로 오버라이드해 둔다
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                        """
                        .formatted(overriddenSat)))
        .andExpect(status().isOk());

    // 정기 패턴을 09~18시 → 09~13시로 변경(오후가 이제 POSSIBLE로 계산돼야 함)
    isolated.applyUpdate(
        "격리 테스트용",
        "SAT",
        LocalTime.of(9, 0),
        LocalTime.of(13, 0),
        2,
        VacationApplyPeriod.ANY,
        false,
        true);
    regularScheduleRepository.save(isolated);

    // 오버라이드된 날짜 — 정기가 바뀌어도 그대로 고정. 각 날짜를 개별 조회한다 — 두 날짜 사이의
    // 평일들이 공유 R1/R2와 매칭돼 함께 잡히면 days[] 인덱스 가정이 깨지므로 범위 조회를 쓰지 않는다
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", overriddenSat.toString())
                .param("endDate", overriddenSat.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"));

    // 오버라이드 없는 날짜 — 새 정기 패턴(09~13시)을 그대로 따라감
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", plainSat.toString())
                .param("endDate", plainSat.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("POSSIBLE"));
  }

  // 시나리오 12 — O1.3 버그 회귀: 정기 있는 평일에 슬롯 3개를 전부 POSSIBLE로 명시해도 삭제되지 않고 그대로 저장된다
  @Test
  void explicitAllPossibleOnWorkday_isNotDeleted_o13BugRegression() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}
                        """
                        .formatted(fri)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").exists())
        .andExpect(jsonPath("$.data.items[0].morningStatus").value("POSSIBLE"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", fri.toString())
                .param("endDate", fri.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("POSSIBLE"));
  }

  // 시나리오 13 — 정기가 아예 없는 날(주말)에도 개별 일정만 단독 등록 가능, sparse에서 벗어남
  // 일요일을 쓴다(토요일은 regularPatternChange의 격리 정기 일정과 공유 DB에서 겹칠 수 있음)
  @Test
  void standaloneWeekendRegistration_appearsInCalendarWithoutRegular() throws Exception {
    LocalDate standaloneSun = mon.plusDays(20);

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", standaloneSun.toString())
                .param("endDate", standaloneSun.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(0));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                        """
                        .formatted(standaloneSun)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", standaloneSun.toString())
                .param("endDate", standaloneSun.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(1))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("IMPOSSIBLE"));
  }

  // 시나리오 15 — 같은 scheduleDate가 items에 중복되면 400
  @Test
  void duplicateScheduleDate_returns400() throws Exception {
    LocalDate date = mon.plusDays(47);
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [
                          {"scheduleDate": "%s", "uncertain": true},
                          {"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}
                        ]}
                        """
                        .formatted(date, date)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  // 시나리오 16 — slots도 uncertain도 없으면 400
  @Test
  void slotsAndUncertainBothMissing_returns400() throws Exception {
    LocalDate date = mon.plusDays(48);
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items": [{"scheduleDate": "%s"}]}
                    """.formatted(date)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  // 시나리오 17 — slots를 보내면서 3필드 중 하나라도 빠지면 400
  @Test
  void slotsPartialField_returns400() throws Exception {
    LocalDate date = mon.plusDays(49);
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "POSSIBLE"}}]}
                        """
                        .formatted(date)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  // 시나리오 18 — items가 빈 배열이면 400
  @Test
  void emptyItems_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items": []}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }
}
