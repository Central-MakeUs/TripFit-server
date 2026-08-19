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

// GET/POST/PATCH/DELETE /users/schedule/regular·GET /calendar·PATCH /personal 각각은
// UserScheduleControllerTest(mock 단위)·ScheduleServiceTest(mock 단위)·
// PersonalScheduleOverrideIntegrationTest(개별+달력만)로 개별 검증돼 있었지만, 정기 일정
// 생성→목록→수정→달력 반영→삭제까지 이어지는 전체 생명주기와 hasCompletedPreSchedule 파생값 전이는
// 실제 HTTP+MySQL(Testcontainers) 라운드트립으로 검증된 적이 없었다. 이 클래스가 그 공백을 메운다.
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

  // 오늘과 무관하게 항상 조회 윈도우 안인 미래의 월요일을 기준으로 삼는다
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

  // 생성 → 목록 반영 → 달력 반영 → 수정 → 달력 재계산 → 삭제 → 목록·달력에서 사라짐, 한 흐름으로 검증
  @Test
  void createUpdateDelete_reflectsAcrossListAndCalendarAtEachStep() throws Exception {
    // 1. 생성 — 평일 09~18시(아침·오후 불가능, 저녁 가능)
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

    // 2. 목록 — 방금 만든 일정이 그대로 보여야 함
    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(regularId.toString()))
        .andExpect(jsonPath("$.data.items[0].startTime").value("09:00:00"));

    // 3. 달력 — 정기 패턴이 그대로 반영돼야 함
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", monday.toString())
                .param("endDate", monday.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"));

    // 4. 수정 — 13~22시로 시간대를 바꾸면 저녁도 불가능해져야 함
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

    // 5. 목록 — 수정된 내용으로 갱신돼 있어야 함
    mockMvc
        .perform(get("/api/v1/users/schedule/regular").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].title").value("야간 근무"))
        .andExpect(jsonPath("$.data.items[0].startTime").value("13:00:00"));

    // 6. 달력 — 수정된 패턴으로 재계산돼야 함(아침은 이제 가능, 저녁은 불가능)
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", monday.toString())
                .param("endDate", monday.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("IMPOSSIBLE"));

    // 7. 삭제
    mockMvc
        .perform(
            delete("/api/v1/users/schedule/regular/" + regularId)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNoContent());

    // 8. 목록·달력 — 아무 신호도 없으니 목록은 비고, 달력은 그 날짜를 아예 생략해야 함
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

  // 정기 패턴을 실제 PATCH 엔드포인트로 바꿔도, 그 사이 개별 오버라이드로 고정해 둔 날짜는 영향을 받지 않아야 한다
  // (entity 직접 조작이 아니라 생성→개별 오버라이드→수정까지 전부 실제 API 라운드트립으로 검증)
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

    // 월요일만 아침 가능으로 개별 오버라이드
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

    // 09~18시 → 09~13시로 정기 패턴 변경(오후가 이제 가능으로 계산돼야 함)
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

    // 오버라이드된 월요일 — 패턴이 바뀌어도 그대로 고정
    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .param("startDate", overridden.toString())
                .param("endDate", overridden.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"));

    // 오버라이드 없는 화요일 — 새 정기 패턴(09~13시)을 그대로 따라감
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

  // 최초/갱신 판정이 일정 데이터가 아니라 연차·휴일 정보(사전 신청일)에만 달려 있는지 —
  // 정기 일정을 만들어도 최초 그대로여야 하고, 연차를 저장한 뒤에는 정기를 전부 지워도 갱신이어야 한다
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

    // 정기 일정을 만들어도 사전 신청일이 없으면 여전히 최초 입력이다
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

    // "정기 일정이 있나요? → 없어요" 경로 — 전체 삭제 후에도 갱신 입력 상태는 유지된다
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

  // 존재하지 않거나 타인 소유인 정기 일정을 수정·삭제하려 하면 404
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
