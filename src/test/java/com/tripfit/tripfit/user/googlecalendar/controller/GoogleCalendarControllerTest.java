package com.tripfit.tripfit.user.googlecalendar.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarErrorCode;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// GoogleCalendarController 자체 테스트가 없어(B-6) connect/disconnect의 성공·검증 실패·에러 응답 매핑을 검증한다
@ExtendWith(MockitoExtension.class)
class GoogleCalendarControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private GoogleCalendarService googleCalendarService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthentication(USER_ID));
    GoogleCalendarController controller = new GoogleCalendarController(googleCalendarService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthorizedUserArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static UserSummaryResponse summary(boolean connected) {
    return new UserSummaryResponse(
        USER_ID,
        "user@example.com",
        "길동",
        "홍",
        "홍길동",
        null,
        SocialProvider.GOOGLE,
        connected,
        false,
        false,

        true);
  }

  @Test
  void connect_returnsConnectedSummary() throws Exception {
    when(googleCalendarService.connect(eq(USER_ID), eq("auth-code"), isNull()))
        .thenReturn(summary(true));

    mockMvc
        .perform(
            post("/api/v1/users/google-calendar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"authorizationCode":"auth-code"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isGoogleCalendarConnected").value(true));
  }

  @Test
  void connect_blankAuthorizationCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/google-calendar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"authorizationCode":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void connect_whenGoogleConnectFails_returns502() throws Exception {
    when(googleCalendarService.connect(eq(USER_ID), any(), any()))
        .thenThrow(new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_CONNECT_FAILED));

    mockMvc
        .perform(
            post("/api/v1/users/google-calendar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"authorizationCode":"bad-code"}
                    """))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("GOOGLE_CALENDAR_CONNECT_FAILED"));
  }

  @Test
  void disconnect_returnsDisconnectedSummary() throws Exception {
    when(googleCalendarService.disconnect(USER_ID)).thenReturn(summary(false));

    mockMvc
        .perform(delete("/api/v1/users/google-calendar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isGoogleCalendarConnected").value(false));
  }

  @Test
  void disconnect_whenNotConnected_returns409() throws Exception {
    when(googleCalendarService.disconnect(USER_ID))
        .thenThrow(new TripFitException(GoogleCalendarErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));

    mockMvc
        .perform(delete("/api/v1/users/google-calendar"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOOGLE_CALENDAR_NOT_CONNECTED"));
  }
}
