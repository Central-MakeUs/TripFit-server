package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.auth.dto.UserSummaryResponse;
import com.tripfit.tripfit.auth.service.AuthService;
import com.tripfit.tripfit.auth.service.JwtService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
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
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		accessToken = jwtService.createAccessToken(1L);
		when(authService.getCurrentUser(1L)).thenReturn(
				new UserSummaryResponse(1L, "user@example.com", SocialProvider.GOOGLE)
		);
	}

	@Test
	void getMe_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}

	@Test
	void getMe_withValidBearer_returnsUserSummary() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.provider").value("GOOGLE"));
	}

	@Test
	void getMe_withInvalidBearer_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}
}
