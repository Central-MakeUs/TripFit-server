package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class UserWithdrawalService {

  private static final Logger log = LoggerFactory.getLogger(UserWithdrawalService.class);

  private final UserLookupService userLookupService;

  private final GoogleCalendarService googleCalendarService;

  private final KakaoUnlinkClient kakaoUnlinkClient;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  private final UserWithdrawalPersistenceService persistenceService;

  public void withdraw(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {

      return;
    }

    revokeGoogleCalendarIfConnected(userId);
    googleLoginCredentialService.revokeAndDeleteIfPresent(userId);
    unlinkKakaoIfProvider(user);
    appleCredentialService.revokeAndDeleteIfPresent(userId);

    persistenceService.finalizeWithdrawal(userId);
  }

  private void revokeGoogleCalendarIfConnected(UUID userId) {
    try {
      googleCalendarService.revokeIfConnected(userId);
    } catch (Exception exception) {
      log.warn("Google Calendar credential revoke failed", exception);
    }
  }

  private void unlinkKakaoIfProvider(User user) {
    if (user.getProvider() == SocialProvider.KAKAO) {
      kakaoUnlinkClient.unlink(user.getSocialId());
    }
  }
}
