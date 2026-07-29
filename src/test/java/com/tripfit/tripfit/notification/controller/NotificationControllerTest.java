package com.tripfit.tripfit.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver;
import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationType;
import com.tripfit.tripfit.notification.dto.NotificationResponse;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.service.NotificationQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private NotificationQueryService notificationQueryService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(USER_ID));
    NotificationController controller = new NotificationController(notificationQueryService);
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
  void list_returnsRecentNotifications() throws Exception {
    when(notificationQueryService.listRecent(USER_ID))
        .thenReturn(
            List.of(
                new NotificationResponse(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
                    NotificationType.JOIN_COMPLETED,
                    "여행방 참여 알림",
                    "홍길동님이 여행방에 참여했어요! 참여 현황을 확인해보세요.",
                    LandingType.TRAVEL_ROOM_DETAIL,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
                    false,
                    LocalDateTime.of(2026, 8, 1, 9, 0))));

    mockMvc
        .perform(get("/api/v1/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("JOIN_COMPLETED"))
        .andExpect(jsonPath("$.data[0].isRead").value(false));
  }

  @Test
  void markRead_returnsNoContent() throws Exception {
    UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

    mockMvc
        .perform(patch("/api/v1/notifications/{id}/read", notificationId))
        .andExpect(status().isNoContent());

    verify(notificationQueryService).markRead(USER_ID, notificationId);
  }

  @Test
  void markRead_notFound_returns404() throws Exception {
    UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    doThrow(new TripFitException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
        .when(notificationQueryService)
        .markRead(eq(USER_ID), eq(notificationId));

    mockMvc
        .perform(patch("/api/v1/notifications/{id}/read", notificationId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
  }
}
