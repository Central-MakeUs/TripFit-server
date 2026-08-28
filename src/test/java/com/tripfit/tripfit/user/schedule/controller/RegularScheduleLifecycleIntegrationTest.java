package com.tripfit.tripfit.user.schedule.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class RegularScheduleLifecycleIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  private MockMvc mockMvc;

  private ObjectMapper objectMapper;

  private String accessToken;

  private LocalDate monday;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    objectMapper = new ObjectMapper();

    User user =
        new User(
            "regular-lifecycle-" + UUID.randomUUID(),
            SocialProvider.GOOGLE,
            "lifecycle@example.com",
            "닉",
            null);
    user = userRepository.save(user);
    accessToken = jwtService.createAccessToken(user.getId());

    monday = LocalDate.now().plusDays(10).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
  }

  private String bearer() {
    return "Bearer " + accessToken;
  }

  @Test
  void createUpdateDelete_reflectsAcrossListAndCalendarAtEachStep() throws Exception {

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/users/schedule/regular")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {"title": "출근", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "09:00:00", "endTime": "18:00:00", "maxVacationDays": 5, "vacationApplyPeriod": "ONE_WEEK_BEFORE", "halfVacationAvailable": true, "holidayRest": true}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.morningStatus").value("IMPOSSIBLE"))
            .andExpect(jsonPath("$.data.eveningStatus").value("POSSIBLE"))
            .andReturn();
    UUID regularId = extractId(createResult);

    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(regularId.toString()))
        .andExpect(jsonPath("$.data.items[0].startTime").value("09:00:00"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", monday.toString())
                .param("endDate", monday.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/regular/" + regularId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"title": "야간 근무", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "13:00:00", "endTime": "22:00:00", "maxVacationDays": 3, "vacationApplyPeriod": "TWO_WEEKS_BEFORE", "halfVacationAvailable": false, "holidayRest": false}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("야간 근무"))
        .andExpect(jsonPath("$.data.eveningStatus").value("IMPOSSIBLE"));

    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].title").value("야간 근무"))
        .andExpect(jsonPath("$.data.items[0].startTime").value("13:00:00"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", monday.toString())
                .param("endDate", monday.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("IMPOSSIBLE"));

    mockMvc
        .perform(
            delete("/api/v1/users/schedule/regular/" + regularId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", monday.toString())
                .param("endDate", monday.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(0));
  }

  @Test
  void personalOverride_survivesRegularPatternChangeViaRealUpdateEndpoint() throws Exception {
    LocalDate overridden = monday;
    LocalDate plain = monday.plusDays(1);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/users/schedule/regular")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {"title": "출근", "daysOfWeek": "MON,TUE", "startTime": "09:00:00", "endTime": "18:00:00", "maxVacationDays": 2, "vacationApplyPeriod": "ANY", "halfVacationAvailable": false, "holidayRest": true}
                            """))
            .andExpect(status().isCreated())
            .andReturn();
    UUID regularId = extractId(createResult);

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"items": [{"scheduleDate": "%s", "slots": {"morningStatus": "POSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE"}}]}
                        """
                        .formatted(overridden)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/regular/" + regularId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"title": "출근", "daysOfWeek": "MON,TUE", "startTime": "09:00:00", "endTime": "13:00:00", "maxVacationDays": 2, "vacationApplyPeriod": "ANY", "halfVacationAvailable": false, "holidayRest": true}
                        """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", overridden.toString())
                .param("endDate", overridden.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", plain.toString())
                .param("endDate", plain.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("POSSIBLE"));
  }

  @Test
  void hasCompletedPreSchedule_flipsOnVacationPolicySaveOnly_notOnScheduleRows() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasCompletedPreSchedule").value(false));

    mockMvc
        .perform(
            post("/api/v1/users/schedule/regular")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"title": "출근", "daysOfWeek": "MON", "startTime": "09:00:00", "endTime": "18:00:00"}
                        """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasCompletedPreSchedule").value(false));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/vacation-policy")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"maxVacationDays": 2, "vacationApplyPeriod": "ANY", "halfVacationAvailable": false, "holidayRest": true}
                        """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasCompletedPreSchedule").value(true));

    mockMvc
        .perform(
            delete("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasCompletedPreSchedule").value(true));
  }

  @Test
  void updateAndDelete_whenNotOwned_returns404() throws Exception {
    UUID otherUsersId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/regular/" + otherUsersId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"title": "출근", "daysOfWeek": "MON", "startTime": "09:00:00", "endTime": "18:00:00", "maxVacationDays": 2, "vacationApplyPeriod": "ANY", "halfVacationAvailable": false, "holidayRest": true}
                        """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REGULAR_SCHEDULE_NOT_FOUND"));

    mockMvc
        .perform(
            delete("/api/v1/users/schedule/regular/" + otherUsersId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REGULAR_SCHEDULE_NOT_FOUND"));
  }

  private UUID extractId(MvcResult result) throws Exception {
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(root.path("data").path("id").asText());
  }
}
