package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.domain.RefreshToken;
import com.tripfit.tripfit.auth.dto.LoginResponse;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.auth.jwt.JwtService;
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
class DevAuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private JwtService jwtService;

  @Mock
  private RefreshTokenService refreshTokenService;

  @Mock
  private UserSummaryService userSummaryService;

  @InjectMocks
  private DevAuthService devAuthService;

  @BeforeEach
  void setUp() {
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
    lenient().when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);
    lenient()
        .when(refreshTokenService.create(any()))
        .thenReturn(
            new RefreshToken(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "refresh-token",
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusDays(30)));
  }

  @Test
  void devLogin_whenTestUserIdBlank_usesChaeyeonAccount() {
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-chaeyeon"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");

    LoginResponse response = devAuthService.devLogin(null);

    assertThat(response.accessToken()).isEqualTo("access-jwt");
    assertThat(response.user().email()).isEqualTo("dev-test-chaeyeon@tripfit.online");
    assertThat(response.user().nickname()).isEqualTo("채연");
    assertThat(response.user().lastName()).isEqualTo("손");
    assertThat(response.user().firstName()).isEqualTo("채연");
  }

  @Test
  void devLogin_whenKnownTeamIds_useRealNicknames() {
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-soeun"))
        .thenReturn(Optional.empty());
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-giyeon"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");

    LoginResponse soeun = devAuthService.devLogin("soeun");
    LoginResponse giyeon = devAuthService.devLogin("giyeon");

    assertThat(soeun.user().nickname()).isEqualTo("소은");
    assertThat(soeun.user().lastName()).isEqualTo("김");
    assertThat(soeun.user().firstName()).isEqualTo("소은");
    assertThat(giyeon.user().nickname()).isEqualTo("기연");
    assertThat(giyeon.user().lastName()).isEqualTo("방");
    assertThat(giyeon.user().firstName()).isEqualTo("기연");
    assertThat(soeun.user().email()).isNotEqualTo(giyeon.user().email());
  }

  @Test
  void devLogin_whenUnknownTestUserId_fallsBackToGenericNickname() {
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-qa1"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt");

    LoginResponse response = devAuthService.devLogin("qa1");

    assertThat(response.user().nickname()).isEqualTo("테스트유저-qa1");
    assertThat(response.user().firstName()).isNull();
    assertThat(response.user().lastName()).isNull();
  }

  @Test
  void devLogin_whenTestUserExists_reusesUserAndIssuesNewTokens() {
    User existing =
        new User(
            "dev-test-user-soeun", SocialProvider.KAKAO, "dev-test-soeun@tripfit.online", "소은",
            null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-soeun"))
        .thenReturn(Optional.of(existing));
    when(jwtService.createAccessToken(any())).thenReturn("access-jwt-2");

    LoginResponse response = devAuthService.devLogin("soeun");

    verify(userRepository, never()).save(any());
    assertThat(response.accessToken()).isEqualTo("access-jwt-2");
  }

  @Test
  void devLogin_whenTestUserWithdrawn_throwsAuthWithdrawnAccount() {
    User withdrawn =
        new User(
            "dev-test-user-soeun", SocialProvider.KAKAO, "dev-test-soeun@tripfit.online", "소은",
            null);
    withdrawn.setDeletedAt(LocalDateTime.now());
    when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "dev-test-user-soeun"))
        .thenReturn(Optional.of(withdrawn));

    assertThatThrownBy(() -> devAuthService.devLogin("soeun"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_WITHDRAWN_ACCOUNT);

    verify(jwtService, never()).createAccessToken(any());
  }
}
