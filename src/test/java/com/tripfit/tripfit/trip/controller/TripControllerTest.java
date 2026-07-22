package com.tripfit.tripfit.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.TripDetailResponse;
import com.tripfit.tripfit.trip.dto.TripHomeCardResponse;
import com.tripfit.tripfit.trip.dto.TripListQuery;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.service.TripService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class TripControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripService tripService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TripController(tripService))
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
  void createTrip_created() throws Exception {
    when(tripService.createTrip(eq(USER_ID), any()))
        .thenReturn(
            new CreateTripResponse(
                TRIP_ID, TripStatus.ONGOING, TripMemberStatus.JOINED, true));

    mockMvc
        .perform(
            post("/api/v1/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "name": "제주 3박4일",
                          "startRange": "2026-08-01",
                          "endRange": "2026-08-10",
                          "durationNights": 3,
                          "durationDays": 4,
                          "memberCount": 6,
                          "destination": "제주"
                        }
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.tripId").value(TRIP_ID.toString()))
        .andExpect(jsonPath("$.data.myMemberStatus").value("JOINED"))
        .andExpect(jsonPath("$.data.needsScheduleConfirm").value(true))
        .andExpect(jsonPath("$.data.inviteCode").doesNotExist());
  }

  @Test
  void listTrips_ok_defaultAll() throws Exception {
    when(tripService.listMyTrips(eq(USER_ID), any(TripListQuery.class)))
        .thenReturn(new TripListResponse(List.of(sampleHomeCard(true))));

    mockMvc
        .perform(get("/api/v1/trips"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trips[0].pinned").value(true))
        .andExpect(jsonPath("$.data.trips[0].myRole").value("OWNER"));
  }

  @Test
  void listTrips_ok_ongoingScope() throws Exception {
    when(tripService.listMyTrips(eq(USER_ID), any(TripListQuery.class)))
        .thenReturn(new TripListResponse(List.of(sampleHomeCard(false))));

    mockMvc
        .perform(get("/api/v1/trips").param("scope", "ongoing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trips[0].pinned").value(false));
  }

  @Test
  void listTrips_badScope_400() throws Exception {
    mockMvc
        .perform(get("/api/v1/trips").param("scope", "weird"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void joinTrip_ok() throws Exception {
    when(tripService.joinTrip(eq(USER_ID), any())).thenReturn(sampleDetail(false));

    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"ABC234\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.joinedMemberCount").value(1))
        .andExpect(jsonPath("$.data.memberCount").value(6));
  }

  @Test
  void updatePin_ok() throws Exception {
    when(tripService.updatePin(eq(TRIP_ID), eq(USER_ID), any())).thenReturn(sampleDetail(true));

    mockMvc
        .perform(
            patch("/api/v1/trips/" + TRIP_ID + "/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pinned").value(true));
  }

  @Test
  void deleteTrip_noContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/trips/" + TRIP_ID))
        .andExpect(status().isNoContent());
  }

  private static TripHomeCardResponse sampleHomeCard(boolean pinned) {
    return new TripHomeCardResponse(
        TRIP_ID,
        "제주",
        "제주",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 10),
        4,
        3,
        6,
        TripStatus.ONGOING,
        LocalDateTime.of(2026, 7, 20, 12, 0),
        pinned,
        TripMemberRole.OWNER,
        TripMemberStatus.RESPONDED,
        0,
        1,
        1.0 / 6.0,
        List.of(),
        0);
  }

  private static TripDetailResponse sampleDetail(boolean pinned) {
    return new TripDetailResponse(
        TRIP_ID,
        "제주",
        "제주",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 10),
        4,
        3,
        6,
        TripStatus.ONGOING,
        "ABC234",
        null,
        null,
        null,
        LocalDateTime.of(2026, 7, 20, 12, 0),
        pinned,
        TripMemberRole.OWNER,
        TripMemberStatus.RESPONDED,
        0,
        1,
        1.0 / 6.0);
  }
}
