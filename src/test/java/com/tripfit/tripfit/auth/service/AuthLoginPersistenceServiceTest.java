package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthLoginPersistenceServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private UserRepository userRepository;

  @Mock
  private RefreshTokenService refreshTokenService;

  @InjectMocks
  private AuthLoginPersistenceService authLoginPersistenceService;

  @BeforeEach
  void setUp() {
    when(refreshTokenService.create(any()))
        .thenReturn(new IssuedRefreshToken("refresh-token", USER_ID, UUID.randomUUID().toString()));
  }

  @Test
  void persist_whenNewUser_createsUserAndIssuesRefreshToken() {
    OAuthProfile profile =
        new OAuthProfile(
            SocialProvider.GOOGLE,
            "google-sub",
            "user@example.com",
            "홍길동",
            "https://example.com/profile.png",
            null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);

    assertThat(result.user().getEmail()).isEqualTo("user@example.com");
    assertThat(result.user().getNickname()).isEqualTo("홍길동");
    assertThat(result.refreshToken().token()).isEqualTo("refresh-token");
  }

  // Apple relay 등 nickname·profileImageUrl이 없는 신규 프로필은 임의 fallback 없이 null 그대로 저장돼야 함
  @Test
  void persist_whenNicknameMissing_storesNullWithoutFallback() {
    OAuthProfile appleProfile =
        new OAuthProfile(
            SocialProvider.APPLE,
            "apple-sub",
            "relay@privaterelay.appleid.com",
            null,
            null,
            "com.tripfit.app");
    when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, "apple-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(appleProfile);

    assertThat(result.user().getNickname()).isNull();
    assertThat(result.user().getProfileImageUrl()).isNull();
  }

  @Test
  void persist_whenExistingUser_updatesEmailNicknameImageButPreservesNames() {
    User existing =
        new User("google-sub", SocialProvider.GOOGLE, "old@example.com", "old-nick", null);
    existing.applyProfilePatch("길동", "홍", null);
    OAuthProfile profile =
        new OAuthProfile(
            SocialProvider.GOOGLE,
            "google-sub",
            "user@example.com",
            "홍길동",
            "https://example.com/profile.png",
            null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.of(existing));

    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);

    assertThat(result.user()).isSameAs(existing);
    assertThat(existing.getEmail()).isEqualTo("user@example.com");
    assertThat(existing.getNickname()).isEqualTo("홍길동");
    assertThat(existing.getFirstName()).isEqualTo("길동");
    assertThat(existing.getLastName()).isEqualTo("홍");
    verify(userRepository, never()).save(any());
  }

  @Test
  void persist_whenExistingAccountIsWithdrawn_revivesAccount() {
    User withdrawn = new User("google-sub", SocialProvider.GOOGLE, null, null, null);
    withdrawn.markDeleted();
    OAuthProfile profile =
        new OAuthProfile(
            SocialProvider.GOOGLE,
            "google-sub",
            "user@example.com",
            "홍길동",
            null,
            null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, "google-sub"))
        .thenReturn(Optional.of(withdrawn));

    AuthLoginPersistenceService.Result result = authLoginPersistenceService.persist(profile);

    assertThat(withdrawn.getDeletedAt()).isNull();
    assertThat(result.user().getEmail()).isEqualTo("user@example.com");
    verify(userRepository, never()).save(any());
  }
}
