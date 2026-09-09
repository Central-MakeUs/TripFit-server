package com.tripfit.tripfit.auth.controller;

import java.util.UUID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.auth.jwt.JwtProperties;
import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
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
@Import(TestcontainersConfig.class)
class AuthSecurityIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private AuthService authService;

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
    when(authService.getCurrentUser(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .thenReturn(
            new UserSummaryResponse(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "user@example.com",
                null,
                null,
                "홍길동",
                "https://example.com/profile.png",
                SocialProvider.GOOGLE,
                false,
                false,
                false,
                true));
  }

  @Test
  void getMe_withoutBearer_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
  }

  @Test
  void getMe_withValidBearer_returnsUserSummary() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("홍길동"))
        .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/profile.png"))
        .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
        .andExpect(jsonPath("$.data.hasPreSchedule").value(false));
  }

  @Test
  void getMe_withInvalidBearer_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
  }

  @Test
  void getMe_withExpiredBearer_returns401WithExpiredCode() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_EXPIRED"));
  }

  // 회귀 테스트 — 클라이언트가 만료된 액세스 토큰을 Authorization 헤더로 습관적으로 실어 보내도
  // permitAll인 refresh 자체는 막히면 안 됨(고쳐지기 전엔 필터가 컨트롤러 도달 전에 401 AUTH_EXPIRED로 차단했음)
  @Test
  void refresh_withExpiredBearerHeader_stillReachesController() throws Exception {
    when(authService.refresh("refresh-token"))
        .thenReturn(new RefreshResponse("new-access-jwt", "new-refresh-token", 7200L));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {"refreshToken":"refresh-token"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"));
  }

  // 서명 secret은 application-test.yml과 동일 — 이미 만료된 액세스 토큰을 즉시 만들기 위해 만료 시간을 음수로 둠
  private String expiredAccessToken() {
    JwtProperties expiredJwtProperties = new JwtProperties();
    expiredJwtProperties.setSecret("test-jwt-secret-key-at-least-32-characters");
    expiredJwtProperties.setAccessExpirationSeconds(-10);
    JwtService expiredJwtService = new JwtService(expiredJwtProperties);
    return expiredJwtService.createAccessToken(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
  }
}
