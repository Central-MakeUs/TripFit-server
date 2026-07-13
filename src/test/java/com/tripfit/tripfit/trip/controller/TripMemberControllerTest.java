package com.tripfit.tripfit.trip.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.config.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.config.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse.DayPersonal;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse.MemberPersonal;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TripMemberControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID OTHER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private ScheduleService scheduleService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TripMemberController(scheduleService))
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
  void getPersonalSummary_ok() throws Exception {
    when(scheduleService.getMemberPersonalSummary(TRIP_ID, USER_ID))
        .thenReturn(
            new MemberPersonalSummaryResponse(
                List.of(
                    new MemberPersonal(
                        OTHER_ID,
                        "김철수",
                        List.of(
                            new DayPersonal(
                                LocalDate.of(2026, 8, 3),
                                ScheduleStatus.IMPOSSIBLE,
                                ScheduleStatus.POSSIBLE,
                                ScheduleStatus.POSSIBLE,
                                true))))));

    mockMvc
        .perform(get("/api/v1/trips/" + TRIP_ID + "/members/personal-summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.members[0].days[0].uncertain").value(true));
  }

  @Test
  void getPersonalSummary_forbidden() throws Exception {
    when(scheduleService.getMemberPersonalSummary(TRIP_ID, USER_ID))
        .thenThrow(new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/trips/" + TRIP_ID + "/members/personal-summary"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TRIP_ACCESS_DENIED"));
  }
}
