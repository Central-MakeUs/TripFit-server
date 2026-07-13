package com.tripfit.tripfit.user.controller;

import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.service.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class UserSecurityIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UserProfileService userProfileService;

  private MockMvc mockMvc;

  private String accessToken;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    accessToken =
        jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
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
  }

  @Test
  void patchProfile_withoutBearer_returns401() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"길동","lastName":"홍"}
                        """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
  }

  @Test
  void patchProfile_withValidBearer_returns200() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"firstName":"길동","lastName":"홍"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.firstName").value("길동"));
  }
}
