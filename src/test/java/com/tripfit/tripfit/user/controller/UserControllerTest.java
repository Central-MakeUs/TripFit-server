package com.tripfit.tripfit.user.controller;

import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.jwt.JwtAuthentication;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
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
class UserControllerTest {

  @Mock
  private UserProfileService userProfileService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthentication(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")));
    UserController userController = new UserController(userProfileService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(userController)
            .setCustomArgumentResolvers(
                new com.tripfit.tripfit.auth.jwt.AuthorizedUserArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void patchProfile_returnsUpdatedUser() throws Exception {
    when(
        userProfileService
            .updateProfile(eq(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), any()))
        .thenReturn(
            new UserSummaryResponse(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "user@example.com",
                "길동",
                "홍",
                "홍길동",
                null,
                SocialProvider.GOOGLE,
                false,
                false,
                false));

    mockMvc
        .perform(
            patch("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"길동","lastName":"홍"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.firstName").value("길동"))
        .andExpect(jsonPath("$.data.lastName").value("홍"));
  }

  @Test
  void patchProfile_blankFirstName_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"","lastName":"홍"}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void patchMyPage_returnsUpdatedUser() throws Exception {
    when(
        userProfileService
            .updateMyPage(eq(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), any()))
        .thenReturn(
            new UserSummaryResponse(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "user@example.com",
                "철수",
                "김",
                "홍길동",
                null,
                SocialProvider.GOOGLE,
                false,
                false,
                true));

    mockMvc
        .perform(
            patch("/api/v1/users/my-page")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"철수","lastName":"김"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.firstName").value("철수"))
        .andExpect(jsonPath("$.data.lastName").value("김"));
  }

  @Test
  void patchMyPage_blankLastName_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/my-page")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"길동","lastName":""}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void patchOnboarding_skipOnly_returnsUpdatedUser() throws Exception {
    when(
        userProfileService
            .updateOnboarding(eq(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), any()))
        .thenReturn(
            new UserSummaryResponse(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "user@example.com",
                null,
                null,
                "홍길동",
                null,
                SocialProvider.GOOGLE,
                false,
                false,
                true));

    mockMvc
        .perform(
            patch("/api/v1/users/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"isOptionalOnboardingCompleted":true}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isOptionalOnboardingCompleted").value(true))
        .andExpect(jsonPath("$.data.isGoogleCalendarConnected").value(false));
  }
}
