package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.auth.client.OAuthProfile;
import com.tripfit.tripfit.auth.client.SocialTokenVerifier;
import com.tripfit.tripfit.auth.client.SocialTokenVerifierRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private SocialTokenVerifierRegistry verifierRegistry;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private SocialTokenVerifier socialTokenVerifier;

	@InjectMocks
	private AuthService authService;

	private OAuthProfile oAuthProfile;

	@BeforeEach
	void setUp() {
		oAuthProfile = new OAuthProfile(SocialProvider.GOOGLE, "google-sub", "user@example.com");
	}

	@Test
	void login_createsUserAndTokens() {
		when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
		when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
		when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			return new User(user.getSocialId(), user.getProvider(), user.getEmail());
		});
		when(jwtService.createAccessToken(any())).thenReturn("access-jwt");
		when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);
		when(refreshTokenService.create(any())).thenReturn(
				new RefreshToken(1L, "refresh-token", UUID.randomUUID().toString(), LocalDateTime.now().plusDays(14))
		);

		LoginResponse response = authService.login(SocialProvider.GOOGLE, "id-token");

		assertThat(response.accessToken()).isEqualTo("access-jwt");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.user().email()).isEqualTo("user@example.com");
	}

	@Test
	void refresh_returnsNewAccessToken() {
		RefreshToken refreshToken = new RefreshToken(1L, "refresh-token", UUID.randomUUID().toString(), LocalDateTime.now().plusDays(14));
		when(refreshTokenService.validate("refresh-token")).thenReturn(refreshToken);
		when(jwtService.createAccessToken(1L)).thenReturn("new-access-jwt");
		when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);

		RefreshResponse response = authService.refresh("refresh-token");

		assertThat(response.accessToken()).isEqualTo("new-access-jwt");
	}

	@Test
	void refresh_invalidToken_throwsAndDeletesExpired() {
		doThrow(new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH))
				.when(refreshTokenService).validate("expired-token");

		assertThatThrownBy(() -> authService.refresh("expired-token"))
				.isInstanceOf(TripFitException.class)
				.extracting(exception -> ((TripFitException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH);

		verify(refreshTokenService).deleteExpired("expired-token");
	}

	@Test
	void logout_deletesRefreshToken() {
		authService.logout("refresh-token");
		verify(refreshTokenService).delete("refresh-token");
	}
}
