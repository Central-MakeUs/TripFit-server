package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.jwt.JwtService;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifierRegistry;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private SocialTokenVerifierRegistry verifierRegistry;

  @Mock
  private AuthLoginPersistenceService authLoginPersistenceService;

  @Mock
  private JwtService jwtService;

  @Mock
  private RefreshTokenService refreshTokenService;

  @Mock
  private UserSummaryService userSummaryService;

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private SocialTokenVerifier socialTokenVerifier;

  @Mock
  private AppleCredentialService appleCredentialService;

  @Mock
  private GoogleLoginCredentialService googleLoginCredentialService;

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
            "https://example.com/profile.png",
            null);
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
                  u.hasCompletedPreSchedule(),
                  u.isNotificationEnabled());
            });
  }

  private static User persistedUser(OAuthProfile profile) {
    User user =
        new User(
            profile.providerUserId(),
            profile.provider(),
            profile.email(),
            profile.nickname(),
            profile.profileImageUrl());
    user.setId(USER_ID);
    return user;
  }

  private static AuthLoginPersistenceService.Result persistenceResult(User user) {
    return new AuthLoginPersistenceService.Result(
        user, new IssuedRefreshToken("refresh-token", user.getId(), UUID.randomUUID().toString()));
  }

  @Test
  void login_createsUserAndTokens() {
    User user = persistedUser(oAuthProfile);
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(authLoginPersistenceService.persist(oAuthProfile)).thenReturn(persistenceResult(user));
    when(jwtService.createAccessToken(USER_ID)).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    AuthService.LoginResult result =
        authService.login(SocialProvider.GOOGLE, "id-token", "google-auth-code", null);

    assertThat(result.response().accessToken()).isEqualTo("access-jwt");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.response().user().email()).isEqualTo("user@example.com");
    assertThat(result.response().user().firstName()).isNull();
    assertThat(result.response().user().lastName()).isNull();
    assertThat(result.response().user().nickname()).isEqualTo("홍길동");
    assertThat(result.response().user().profileImageUrl())
        .isEqualTo("https://example.com/profile.png");
    assertThat(result.response().user().hasCompletedPreSchedule()).isFalse();
  }

  @Test
  void login_whenAppleWithoutAuthorizationCode_throwsAuthorizationCodeRequired() {
    assertThatThrownBy(() -> authService.login(SocialProvider.APPLE, "id-token", null, null))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED);

    verifyNoInteractions(verifierRegistry, authLoginPersistenceService, appleCredentialService);
  }

  @Test
  void login_whenAppleWithBlankAuthorizationCode_throwsAuthorizationCodeRequired() {
    assertThatThrownBy(() -> authService.login(SocialProvider.APPLE, "id-token", "  ", null))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED);
  }

  @Test
  void login_whenAppleWithAuthorizationCode_savesCredential() {
    OAuthProfile appleProfile =
        new OAuthProfile(
            SocialProvider.APPLE,
            "apple-sub",
            "user@example.com",
            "닉네임",
            null,
            "com.tripfit.service");
    User user = persistedUser(appleProfile);
    when(verifierRegistry.getVerifier(SocialProvider.APPLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(appleProfile);
    when(authLoginPersistenceService.persist(appleProfile)).thenReturn(persistenceResult(user));
    when(jwtService.createAccessToken(USER_ID)).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    authService.login(SocialProvider.APPLE, "id-token", "auth-code", null);

    verify(appleCredentialService)
        .saveIfAuthorizationCodePresent(
            eq(user),
            eq("auth-code"),
            eq("com.tripfit.service"));
  }

  @Test
  void login_whenNotApple_neverCallsAppleCredentialService() {
    User user = persistedUser(oAuthProfile);
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(authLoginPersistenceService.persist(oAuthProfile)).thenReturn(persistenceResult(user));
    when(jwtService.createAccessToken(USER_ID)).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    authService.login(SocialProvider.GOOGLE, "id-token", "google-auth-code", null);

    verify(appleCredentialService, never())
        .saveIfAuthorizationCodePresent(any(), any(), any());
  }

  @Test
  void login_whenGoogleWithoutAuthorizationCode_throwsAuthorizationCodeRequired() {
    assertThatThrownBy(() -> authService.login(SocialProvider.GOOGLE, "id-token", null, null))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED);

    verifyNoInteractions(
        verifierRegistry,
        authLoginPersistenceService,
        googleLoginCredentialService);
  }

  @Test
  void login_whenGoogleWithBlankAuthorizationCode_throwsAuthorizationCodeRequired() {
    assertThatThrownBy(() -> authService.login(SocialProvider.GOOGLE, "id-token", "  ", null))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED);
  }

  @Test
  void login_whenGoogleWithAuthorizationCode_savesCredential() {
    User user = persistedUser(oAuthProfile);
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(authLoginPersistenceService.persist(oAuthProfile)).thenReturn(persistenceResult(user));
    when(jwtService.createAccessToken(USER_ID)).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    authService.login(SocialProvider.GOOGLE, "id-token", "google-auth-code", null);

    verify(googleLoginCredentialService)
        .saveIfAuthorizationCodePresent(eq(user), eq("google-auth-code"), isNull());
  }

  @Test
  void login_whenGoogleWithRedirectUri_passesItToCredentialService() {
    User user = persistedUser(oAuthProfile);
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(socialTokenVerifier);
    when(socialTokenVerifier.verify("id-token")).thenReturn(oAuthProfile);
    when(authLoginPersistenceService.persist(oAuthProfile)).thenReturn(persistenceResult(user));
    when(jwtService.createAccessToken(USER_ID)).thenReturn("access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    authService.login(
        SocialProvider.GOOGLE,
        "id-token",
        "google-auth-code",
        "https://tripfit.online/auth/google/callback");

    verify(googleLoginCredentialService)
        .saveIfAuthorizationCodePresent(
            eq(user),
            eq("google-auth-code"),
            eq("https://tripfit.online/auth/google/callback"));
  }

  @Test
  void refresh_rotatesAndReturnsNewAccessAndRefreshToken() {
    IssuedRefreshToken rotated =
        new IssuedRefreshToken(
            "new-refresh-token",
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            UUID.randomUUID().toString());
    when(refreshTokenService.rotate("refresh-token")).thenReturn(rotated);
    when(jwtService.createAccessToken(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .thenReturn("new-access-jwt");
    when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

    AuthService.RefreshResult result = authService.refresh("refresh-token");

    assertThat(result.response().accessToken()).isEqualTo("new-access-jwt");
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
  }

  @Test
  void refresh_reusedToken_propagatesReuseException() {
    doThrow(new TripFitException(AuthErrorCode.AUTH_REFRESH_REUSE))
        .when(refreshTokenService)
        .rotate("stolen-token");

    assertThatThrownBy(() -> authService.refresh("stolen-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_REFRESH_REUSE);
  }

  @Test
  void logout_deletesRefreshToken() {
    authService.logout("refresh-token");

    verify(refreshTokenService).delete("refresh-token");
  }

  @Test
  void logout_withoutRefreshTokenCookie_skipsDeleteWithoutFailing() {
    authService.logout(null);

    verifyNoInteractions(refreshTokenService);
  }
}
