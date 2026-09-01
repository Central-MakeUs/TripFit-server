package com.tripfit.tripfit.trip.membership.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse.CalendarDay;
import com.tripfit.tripfit.trip.schedule.dto.MemberScheduleCalendarResponse.MemberCalendar;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse;
import com.tripfit.tripfit.trip.membership.dto.TripMembersResponse.TripMemberItemResponse;
import com.tripfit.tripfit.trip.service.TripService;
import java.time.Instant;
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
  private TripService tripService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthentication(USER_ID, "test-jti", Instant.now().plusSeconds(3600)));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TripMemberController(tripService))
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
  void listMembers_ok() throws Exception {
    when(tripService.listMembers(TRIP_ID, USER_ID))
        .thenReturn(
            new TripMembersResponse(
                2,
                1,
                1.0,
                List.of(
                    new TripMemberItemResponse(
                        OTHER_ID, "김철수", TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
                        false))));

    mockMvc
        .perform(get("/api/v1/trips/" + TRIP_ID + "/members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberFillRate").value(1.0));
  }

  @Test
  void removeMember_ok() throws Exception {
    when(tripService.removeMember(TRIP_ID, USER_ID, OTHER_ID))
        .thenReturn(
            new TripMembersResponse(
                6,
                1,
                1.0 / 6,
                List.of(
                    new TripMemberItemResponse(
                        USER_ID, "홍길동", TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
                        false))));

    mockMvc
        .perform(delete("/api/v1/trips/" + TRIP_ID + "/members/" + OTHER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeMemberCount").value(1))
        .andExpect(jsonPath("$.data.members[0].userId").value(USER_ID.toString()));
  }

  @Test
  void leaveTrip_noContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/trips/" + TRIP_ID + "/members/me"))
        .andExpect(status().isNoContent());
  }

  @Test
  void getScheduleCalendar_ok() throws Exception {
    when(tripService.getMemberScheduleCalendar(TRIP_ID, USER_ID))
        .thenReturn(
            new MemberScheduleCalendarResponse(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                false,
                List.of(
                    new MemberCalendar(
                        OTHER_ID,
                        "김철수",
                        TripMemberRole.MEMBER,
                        TripMemberStatus.ACTIVE,
                        List.of(
                            new CalendarDay(
                                LocalDate.of(2026, 8, 3),
                                ScheduleStatus.IMPOSSIBLE,
                                ScheduleStatus.POSSIBLE,
                                ScheduleStatus.POSSIBLE,
                                true))))));

    mockMvc
        .perform(get("/api/v1/trips/" + TRIP_ID + "/members/schedule-calendar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readOnly").value(false))
        .andExpect(jsonPath("$.data.members[0].days[0].uncertain").value(true));
  }
}
