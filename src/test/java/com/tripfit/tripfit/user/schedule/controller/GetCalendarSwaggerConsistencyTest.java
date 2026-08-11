package com.tripfit.tripfit.user.schedule.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// GET /users/schedule/calendar의 Swagger(@ApiResponse 200/400 예시)가 실제 컨트롤러 동작과
// 어긋나지 않는지 검증한다 — O1.4에서 이 API 응답 모양 자체는 안 바뀌었지만, 실제로 확인은 안 했었다
@SpringBootTest
@ActiveProfiles("test")
class GetCalendarSwaggerConsistencyTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RegularScheduleRepository regularScheduleRepository;

  private MockMvc mockMvc;

  private String accessToken;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    User user =
        new User(
            "get-calendar-swagger-" + UUID.randomUUID(),
            SocialProvider.GOOGLE,
            "getcal@example.com",
            "닉",
            null);
    user = userRepository.save(user);
    accessToken = jwtService.createAccessToken(user.getId());
    regularScheduleRepository.save(
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI,SAT,SUN",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            VacationApplyPeriod.ANY,
            false,
            true));
  }

  // 200 @ApiResponse 예시가 선언한 필드(startDate/endDate/days[].date/morningStatus/afternoonStatus/
  // eveningStatus/uncertain)가 실제 응답에도 그대로 있는지
  @Test
  void okExample_fieldShape_matchesActualResponse() throws Exception {
    LocalDate date = LocalDate.now().plusDays(15);

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("startDate", date.toString())
                .param("endDate", date.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.startDate").value(date.toString()))
        .andExpect(jsonPath("$.data.endDate").value(date.toString()))
        .andExpect(jsonPath("$.data.days[0].date").value(date.toString()))
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].afternoonStatus").value("IMPOSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].eveningStatus").value("POSSIBLE"))
        .andExpect(jsonPath("$.data.days[0].uncertain").value(false));
  }

  // 400 @ApiResponse 예시("조회 구간이 허용 윈도우 밖")가 실제 에러 바디와 일치하는지 — startDate가 오늘 이전
  @Test
  void badRequestExample_startBeforeToday_matchesActualErrorBody() throws Exception {
    LocalDate yesterday = LocalDate.now().minusDays(1);

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("startDate", yesterday.toString())
                .param("endDate", LocalDate.now().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
  }
}
