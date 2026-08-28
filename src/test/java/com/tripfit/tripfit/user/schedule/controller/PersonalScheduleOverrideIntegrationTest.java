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
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
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
    user.applyProfilePatch("길동", "홍", null);

    user.applyVacationPolicy(2, VacationApplyPeriod.ANY, false, true);
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

    regularScheduleRepository.save(
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0)));

    regularScheduleRepository.save(
        RegularSchedule.create(
            user,
            "저녁 수업",
            "WED",
            LocalTime.of(19, 0),
            LocalTime.of(21, 0)));
  }

  private String bearer() {
    return "Bearer " + accessToken;
  }

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

  @Test
  void partialOverride_thenFullOverride_thenUncertainRoundtripNeverTouchesSlots_thenCombined()
      throws Exception {

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

  @Test
  void regularPatternChange_overriddenDateFrozen_plainDateFollowsNewPattern() throws Exception {
    RegularSchedule isolated =
        regularScheduleRepository.save(
            RegularSchedule.create(
                user,
                "격리 테스트용",
                "SAT",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));
    LocalDate overriddenSat = mon.plusDays(26);
    LocalDate plainSat = mon.plusDays(33);

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

    isolated.applyUpdate(
        "격리 테스트용",
        "SAT",
        LocalTime.of(9, 0),
        LocalTime.of(13, 0));
    regularScheduleRepository.save(isolated);

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", overriddenSat.toString())
                .param("endDate", overriddenSat.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"));

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
