package com.tripfit.tripfit.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.service.DeviceTokenService;
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

@ExtendWith(MockitoExtension.class)
class DeviceTokenControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private DeviceTokenService deviceTokenService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
    DeviceTokenController controller = new DeviceTokenController(deviceTokenService);
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

  @Test
  void register_returnsNoContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/device-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"fcm-token-1","deviceType":"ANDROID"}
                    """))
        .andExpect(status().isNoContent());

    verify(deviceTokenService).registerToken(eq(USER_ID), any());
  }

  @Test
  void register_blankToken_returns400() throws Exception {
    doThrow(new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED))
        .when(deviceTokenService)
        .registerToken(eq(USER_ID), any());

    mockMvc
        .perform(
            post("/api/v1/notifications/device-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"","deviceType":"ANDROID"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_TOKEN_REQUIRED"));
  }

  @Test
  void unregister_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/notifications/device-tokens").param("token", "fcm-token-1"))
        .andExpect(status().isNoContent());

    verify(deviceTokenService).unregisterToken(USER_ID, "fcm-token-1");
  }

  @Test
  void unregister_notFound_returns404() throws Exception {
    doThrow(new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_NOT_FOUND))
        .when(deviceTokenService)
        .unregisterToken(USER_ID, "missing-token");

    mockMvc
        .perform(delete("/api/v1/notifications/device-tokens").param("token", "missing-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_TOKEN_NOT_FOUND"));
  }
}
