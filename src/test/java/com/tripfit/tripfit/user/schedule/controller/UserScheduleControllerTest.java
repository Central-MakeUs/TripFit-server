package com.tripfit.tripfit.user.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse.PersonalScheduleItemResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserScheduleControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID REGULAR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

  private static final UUID PERSONAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440088");

  @Mock
  private ScheduleService scheduleService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new UserScheduleController(scheduleService))
            .setCustomArgumentResolvers(new AuthorizedUserArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void postRegular_returnsSlotStatuses() throws Exception {
    when(scheduleService.createRegular(eq(USER_ID), any()))
        .thenReturn(
            new RegularScheduleResponse(
                REGULAR_ID,
                "출근",
                "MON,TUE,WED,THU,FRI",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleStatus.IMPOSSIBLE,
                ScheduleStatus.IMPOSSIBLE,
                ScheduleStatus.POSSIBLE,
                5,
                VacationApplyPeriod.ONE_WEEK_BEFORE,
                true,
                true));

    mockMvc
        .perform(
            post("/api/v1/users/schedule/regular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "title": "출근",
                          "daysOfWeek": "MON,TUE,WED,THU,FRI",
                          "startTime": "09:00:00",
                          "endTime": "18:00:00",
                          "maxVacationDays": 5,
                          "vacationApplyPeriod": "ONE_WEEK_BEFORE",
                          "halfVacationAvailable": true,
                          "holidayRest": true
                        }
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.eveningStatus").value("POSSIBLE"));
  }

  @Test
  void patchRegular_updatesFullSchedule() throws Exception {
    when(scheduleService.updateRegular(eq(USER_ID), eq(REGULAR_ID), any()))
        .thenReturn(
            new RegularScheduleResponse(
                REGULAR_ID,
                "야간 근무",
                "MON,WED,FRI",
                LocalTime.of(13, 0),
                LocalTime.of(22, 0),
                ScheduleStatus.POSSIBLE,
                ScheduleStatus.IMPOSSIBLE,
                ScheduleStatus.IMPOSSIBLE,
                3,
                VacationApplyPeriod.TWO_WEEKS_BEFORE,
                false,
                false));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/regular/" + REGULAR_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "title": "야간 근무",
                          "daysOfWeek": "MON,WED,FRI",
                          "startTime": "13:00:00",
                          "endTime": "22:00:00",
                          "maxVacationDays": 3,
                          "vacationApplyPeriod": "TWO_WEEKS_BEFORE",
                          "halfVacationAvailable": false,
                          "holidayRest": false
                        }
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("야간 근무"))
        .andExpect(jsonPath("$.data.startTime").value("13:00:00"))
        .andExpect(jsonPath("$.data.eveningStatus").value("IMPOSSIBLE"));
  }

  @Test
  void putPersonal_returnsDateLevelUncertain() throws Exception {
    when(scheduleService.upsertPersonal(eq(USER_ID), any()))
        .thenReturn(
            new PersonalScheduleResponse(
                List.of(
                    new PersonalScheduleItemResponse(
                        PERSONAL_ID,
                        LocalDate.of(2026, 8, 3),
                        ScheduleStatus.IMPOSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        true))));

    mockMvc
        .perform(
            patch("/api/v1/users/schedule/personal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "items": [
                            {
                              "scheduleDate": "2026-08-03",
                              "morningStatus": "IMPOSSIBLE",
                              "afternoonStatus": "POSSIBLE",
                              "eveningStatus": "POSSIBLE",
                              "uncertain": true
                            }
                          ]
                        }
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].uncertain").value(true))
        .andExpect(jsonPath("$.data.items[0].morningStatus").value("IMPOSSIBLE"));
  }

  @Test
  void getPersonal_ok() throws Exception {
    when(
        scheduleService.getPersonal(
            USER_ID,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10)))
        .thenReturn(new PersonalScheduleResponse(List.of()));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/personal")
                .param("startDate", "2026-08-01")
                .param("endDate", "2026-08-10"))
        .andExpect(status().isOk());
  }

  @Test
  void getCalendar_ok() throws Exception {
    when(scheduleService.getCalendar(eq(USER_ID), any(), any()))
        .thenReturn(
            new ScheduleCalendarResponse(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7),
                List.of(
                    new ScheduleCalendarResponse.CalendarDayResponse(
                        LocalDate.of(2026, 8, 3),
                        ScheduleStatus.IMPOSSIBLE,
                        ScheduleStatus.IMPOSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        false))));

    mockMvc
        .perform(
            get("/api/v1/users/schedule/calendar")
                .param("startDate", "2026-08-01")
                .param("endDate", "2026-08-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].date").value("2026-08-03"))
        .andExpect(jsonPath("$.data.days[0].morningStatus").value("IMPOSSIBLE"));
  }
}
