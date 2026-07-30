package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.oauth.AppleNotificationEvent;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleNotificationServiceTest {

  private static final String SUB = "001234.abcd1234.5678";

  @Mock
  private UserRepository userRepository;

  @Mock
  private RefreshTokenService refreshTokenService;

  private AppleNotificationService appleNotificationService;

  @BeforeEach
  void setUp() {
    appleNotificationService = new AppleNotificationService(userRepository, refreshTokenService);
  }

  @Test
  void handle_consentRevoked_revokesRefreshTokensButKeepsAccount() {
    User user = new User(SUB, SocialProvider.APPLE, null, "닉네임", null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, SUB))
        .thenReturn(Optional.of(user));

    appleNotificationService.handle(
        new AppleNotificationEvent("consent-revoked", SUB, 1234567890L, null, null));

    verify(refreshTokenService).revokeAllForUser(user.getId());
    assertThat(user.getDeletedAt()).isNull();
  }

  @Test
  void handle_accountDelete_softDeletesAndRevokesRefreshTokens() {
    User user = new User(SUB, SocialProvider.APPLE, null, "닉네임", null);
    when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, SUB))
        .thenReturn(Optional.of(user));

    appleNotificationService.handle(
        new AppleNotificationEvent("account-delete", SUB, 1234567890L, null, null));

    verify(refreshTokenService).revokeAllForUser(user.getId());
    assertThat(user.getDeletedAt()).isNotNull();
  }

  @Test
  void handle_emailDisabled_noDbInteraction() {
    appleNotificationService.handle(
        new AppleNotificationEvent(
            "email-disabled", SUB, 1234567890L, "user@privaterelay.appleid.com", true));

    verifyNoInteractions(userRepository, refreshTokenService);
  }

  @Test
  void handle_unknownSub_noOp() {
    when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, SUB))
        .thenReturn(Optional.empty());

    appleNotificationService.handle(
        new AppleNotificationEvent("consent-revoked", SUB, 1234567890L, null, null));

    verify(refreshTokenService, never()).revokeAllForUser(any());
  }

  @Test
  void handle_unrecognizedType_noOp() {
    appleNotificationService.handle(
        new AppleNotificationEvent("some-future-event", SUB, 1234567890L, null, null));

    verifyNoInteractions(userRepository, refreshTokenService);
  }
}
