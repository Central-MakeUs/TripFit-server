package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifierRegistry;
import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.dto.RefreshResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  private UserSummaryService userSummaryService;

  @Mock
  private SocialTokenVerifier socialTokenVerifier;

  @InjectMocks
  private AuthService authService;

  private OAuthProfile oAuthProfile;

  @BeforeEach
  void setUp() {
    oAuthProfile =
        new OAuthProfile(
            SocialProvider.GOOGLE,
            "google-sub",
            "user@example.com",
            "홍길동",
            "https://example.com/profile.png");
    lenient()
        .when(userSummaryService.toSummary(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              return new com.tripfit.tripfit.user.dto.UserSummaryResponse(
                  u.getId(),
                  u.getEmail(),
                  u.getFirstName(),
                  u.getLastName(),
                  u.getNickname(),
                  u.getProfileImageUrl(),
                  u.getProvider(),
                  u.isGoogleCalendarConnected(),
                  false,
                  u.isAllFree());
            });
  }

  @Test
  void login_createsUserAndTokens() {
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);
    when(refreshTokenService.create(any()))
        .thenReturn(
            new RefreshToken(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "refresh-token",
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusDays(30)));

    LoginResponse response = authService.login(SocialProvider.GOOGLE, "id-token");

    assertThat(response.accessToken()).isEqualTo("access-jwt");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.user().email()).isEqualTo("user@example.com");
    assertThat(response.user().firstName()).isNull();
    assertThat(response.user().lastName()).isNull();
    assertThat(response.user().nickname()).isEqualTo("홍길동");
    assertThat(response.user().profileImageUrl()).isEqualTo("https://example.com/profile.png");
    assertThat(response.user().hasPreSchedule()).isFalse();
  }

  @Test
  void login_whenProfileNameSet_preservesNamesOnRelogin() {
    User existing =
        new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "old-nick", null);
    existing.setFirstName("길동");
    existing.setLastName("홍");
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.of(existing));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);
    when(refreshTokenService.create(any()))
        .thenReturn(
            new RefreshToken(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "refresh-token",
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusDays(30)));

    LoginResponse response = authService.login(SocialProvider.GOOGLE, "id-token");

    assertThat(existing.getFirstName()).isEqualTo("길동");
    assertThat(existing.getLastName()).isEqualTo("홍");
    assertThat(response.user().firstName()).isEqualTo("길동");
    assertThat(response.user().lastName()).isEqualTo("홍");
  }

  @Test
  void login_whenNicknameMissing_storesNullWithoutFallback() {
    OAuthProfile appleProfile =
        new OAuthProfile(
            SocialProvider.APPLE, "apple-sub", "relay@privaterelay.appleid.com", null, null);
    when(verifierRegistry.getVerifier(SocialProvider.APPLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(appleProfile);
    when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, "apple-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);
    when(refreshTokenService.create(any()))
        .thenReturn(
            new RefreshToken(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "refresh-token",
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusDays(30)));

    LoginResponse response = authService.login(SocialProvider.APPLE, "id-token");

    assertThat(response.user().nickname()).isNull();
    assertThat(response.user().profileImageUrl()).isNull();
  }

  @Test
  void login_whenExistingAccountIsWithdrawn_throwsAuthWithdrawnAccount() {
    User withdrawn =
        new User("google-sub", SocialProvider.GOOGLE, null, null, null);
    withdrawn.setDeletedAt(LocalDateTime.now());
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.of(withdrawn));

    assertThatThrownBy(() -> authService.login(SocialProvider.GOOGLE, "id-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_WITHDRAWN_ACCOUNT);

    verify(userRepository, org.mockito.Mockito.never()).save(any());
    verify(jwtService, org.mockito.Mockito.never()).createAccessToken(any());
  }

  @Test
  void refresh_returnsNewAccessToken() {
    RefreshToken refreshToken =
        new RefreshToken(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"), "refresh-token",
            UUID.randomUUID().toString(), LocalDateTime.now().plusDays(30));
    when(refreshTokenService.validate("refresh-token")).thenReturn(refreshToken);
    when(jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .thenReturn("new-access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);

    RefreshResponse response = authService.refresh("refresh-token");

    assertThat(response.accessToken()).isEqualTo("new-access-jwt");
  }

  @Test
  void refresh_invalidToken_throwsAndDeletesExpired() {
    doThrow(new TripFitException(AuthErrorCode.AUTH_INVALID_REFRESH))
        .when(refreshTokenService)
        .validate("expired-token");

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
