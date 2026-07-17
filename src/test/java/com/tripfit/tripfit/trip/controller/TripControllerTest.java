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

import com.tripfit.tripfit.auth.config.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.config.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripResponse;
import com.tripfit.tripfit.trip.dto.TripListResponse;
import com.tripfit.tripfit.trip.dto.TripSummaryResponse;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.service.TripService;
import java.time.LocalDate;
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
        .thenReturn(new CreateTripResponse(TRIP_ID, "ABC234", TripStatus.ONGOING));

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
                          "durationDays": 4,
                          "targetMemberCount": 6,
                          "destination": "제주"
                        }
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.inviteCode").value("ABC234"));
  }

  @Test
  void listTrips_ok() throws Exception {
    when(tripService.listMyTrips(USER_ID))
        .thenReturn(
            new TripListResponse(
                List.of(
                    new TripSummaryResponse(
                        TRIP_ID,
                        "제주",
                        "제주",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 10),
                        4,
                        6,
                        TripStatus.ONGOING,
                        "ABC234",
                        null,
                        null,
                        null,
                        true,
                        TripMemberStatus.JOINED,
                        0,
                        1))));

    mockMvc
        .perform(get("/api/v1/trips"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trips[0].pinned").value(true));
  }

  @Test
  void joinTrip_ok() throws Exception {
    when(tripService.joinTrip(eq(USER_ID), any()))
        .thenReturn(
            new TripSummaryResponse(
                TRIP_ID,
                "제주",
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                4,
                6,
                TripStatus.ONGOING,
                "ABC234",
                null,
                null,
                null,
                false,
                TripMemberStatus.JOINED,
                0,
                2));

    mockMvc
        .perform(
            post("/api/v1/trips/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"ABC234\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberCount").value(2));
  }

  @Test
  void updatePin_ok() throws Exception {
    when(tripService.updatePin(eq(TRIP_ID), eq(USER_ID), any()))
        .thenReturn(
            new TripSummaryResponse(
                TRIP_ID,
                "제주",
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                4,
                6,
                TripStatus.ONGOING,
                "ABC234",
                null,
                null,
                null,
                true,
                TripMemberStatus.JOINED,
                0,
                1));

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

  @Test
  void submitSchedule_notOngoing() throws Exception {
    when(tripService.submitSchedule(TRIP_ID, USER_ID))
        .thenThrow(new TripFitException(TripErrorCode.TRIP_NOT_ONGOING));

    mockMvc
        .perform(post("/api/v1/trips/" + TRIP_ID + "/schedule/submit"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRIP_NOT_ONGOING"));
  }
}
