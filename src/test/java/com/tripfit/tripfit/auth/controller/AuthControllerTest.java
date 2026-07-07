package com.tripfit.tripfit.auth.controller;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.dto.UserSummaryResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AuthController authController = new AuthController(authService);
		mockMvc = MockMvcBuilders.standaloneSetup(authController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void login_returnsTokens() throws Exception {
		when(authService.login(eq(SocialProvider.GOOGLE), eq("google-id-token"))).thenReturn(
				new LoginResponse(
						"access-jwt",
						"refresh-token",
						3600L,
						new UserSummaryResponse(1L, "user@example.com", SocialProvider.GOOGLE)
				)
		);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"provider":"GOOGLE","token":"google-id-token"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
				.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.data.user.id").value(1));
	}

	@Test
	void refresh_returnsAccessToken() throws Exception {
		when(authService.refresh("refresh-token")).thenReturn(new RefreshResponse("new-access-jwt", 3600L));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"refresh-token"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"));
	}

	@Test
	void logout_returns204() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"refresh-token"}
								"""))
				.andExpect(status().isNoContent());
	}

	@Test
	void login_missingToken_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"provider":"GOOGLE","token":""}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[0].field").value("token"));
	}

	@Test
	void login_invalidToken_returns401() throws Exception {
		doThrow(new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN))
				.when(authService).login(any(), any());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"provider":"GOOGLE","token":"bad-token"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}
}
