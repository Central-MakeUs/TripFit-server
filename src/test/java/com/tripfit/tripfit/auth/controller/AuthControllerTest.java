package com.tripfit.tripfit.auth.controller;

import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.oauth.AppleNotificationEvent;
import com.tripfit.tripfit.auth.oauth.AppleNotificationVerifier;
import com.tripfit.tripfit.auth.service.AppleNotificationService;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock
  private AuthService authService;

  @Mock
  private AppleNotificationVerifier appleNotificationVerifier;

  @Mock
  private AppleNotificationService appleNotificationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AuthController authController =
        new AuthController(authService, appleNotificationVerifier, appleNotificationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void login_returnsTokens() throws Exception {
    when(authService.login(eq(SocialProvider.GOOGLE), eq("google-id-token"), any(), any()))
        .thenReturn(new LoginResponse("access-jwt", "refresh-token", 7200L, sampleUserSummary()));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"provider":"GOOGLE","token":"google-id-token"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.data.user.id").value("550e8400-e29b-41d4-a716-446655440001"));
  }

  @Test
  void refresh_returnsRotatedAccessAndRefreshToken() throws Exception {
    when(authService.refresh("refresh-token"))
        .thenReturn(new RefreshResponse("new-access-jwt", "new-refresh-token", 7200L));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"refreshToken":"refresh-token"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
  }

  @Test
  void refresh_reusedToken_returns401WithReuseCode() throws Exception {
    doThrow(new TripFitException(AuthErrorCode.AUTH_REFRESH_REUSE))
        .when(authService)
        .refresh("stolen-token");

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"refreshToken":"stolen-token"}
                        """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE"));
  }

  @Test
  void logout_returns204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"refreshToken":"refresh-token"}
                        """))
        .andExpect(status().isNoContent());

    verify(authService).logout("refresh-token", null);
  }

  // 클라이언트가 accessToken을 같이 보내면 그대로 서비스에 전달돼야 함(즉시 블랙리스트 등록용)
  @Test
  void logout_withAccessToken_passesItToService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"refreshToken":"refresh-token","accessToken":"current-access-jwt"}
                        """))
        .andExpect(status().isNoContent());

    verify(authService).logout("refresh-token", "current-access-jwt");
  }

  @Test
  void login_missingToken_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"provider":"GOOGLE","token":""}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.errors[0].field").value("token"));
  }

  @Test
  void login_appleWithoutAuthorizationCode_returns400() throws Exception {
    doThrow(new TripFitException(AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED))
        .when(authService)
        .login(eq(SocialProvider.APPLE), eq("apple-id-token"), any(), any());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"provider":"APPLE","token":"apple-id-token"}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED"));
  }

  @Test
  void login_invalidToken_returns401() throws Exception {
    doThrow(new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID))
        .when(authService)
        .login(any(), any(), any(), any());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"provider":"GOOGLE","token":"bad-token"}
                        """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_SOCIAL_TOKEN_INVALID"));
  }

  @Test
  void appleNotifications_valid_returns200AndDelegatesToService() throws Exception {
    AppleNotificationEvent event =
        new AppleNotificationEvent("consent-revoked", "apple-sub", 1234567890L, null, null);
    when(appleNotificationVerifier.verify("signed-jwt")).thenReturn(event);

    mockMvc
        .perform(
            post("/api/v1/auth/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"payload":"signed-jwt"}
                        """))
        .andExpect(status().isOk());

    verify(appleNotificationService).handle(event);
  }

  @Test
  void appleNotifications_invalidPayload_returns400() throws Exception {
    doThrow(new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD))
        .when(appleNotificationVerifier)
        .verify(any());

    mockMvc
        .perform(
            post("/api/v1/auth/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"payload":"not-a-jwt"}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD"));
  }

  @Test
  void appleNotifications_signatureInvalid_returns401() throws Exception {
    doThrow(new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID))
        .when(appleNotificationVerifier)
        .verify(any());

    mockMvc
        .perform(
            post("/api/v1/auth/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"payload":"forged-jwt"}
                        """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID"));
  }

  @Test
  void appleNotifications_missingPayload_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"payload":""}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.errors[0].field").value("payload"));
  }

  private static UserSummaryResponse sampleUserSummary() {
    return new UserSummaryResponse(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        "user@example.com",
        null,
        null,
        "홍길동",
        "https://example.com/profile.png",
        SocialProvider.GOOGLE,
        false,
        false,

        true);
  }
}
