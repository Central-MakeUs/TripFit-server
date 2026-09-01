package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.oauth.AppleNotificationEvent;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleNotificationService {

  private static final Logger log = LoggerFactory.getLogger(AppleNotificationService.class);

  private static final String CONSENT_REVOKED = "consent-revoked";

  private static final String ACCOUNT_DELETE = "account-delete";

  private static final String EMAIL_ENABLED = "email-enabled";

  private static final String EMAIL_DISABLED = "email-disabled";

  private final UserRepository userRepository;

  private final RefreshTokenService refreshTokenService;

  // Apple 서버로부터 전달받은 알림(S2S Webhook) 이벤트를 처리합니다.
  // 연동 해제(consent-revoked) 시 세션을 종료하고, 계정 삭제(account-delete) 시 소프트 딜리트를 수행합니다.
  @Transactional
  public void handle(AppleNotificationEvent event) {
    switch (event.type()) {
      case EMAIL_ENABLED, EMAIL_DISABLED -> {

        SocialIntegrationLog.info(
            log,
            notificationContext(),
            "Apple notification " + event.type() + " received (email 컬럼 미보유. 로그만)");
      }
      case CONSENT_REVOKED ->
        findUser(event.sub()).ifPresentOrElse(this::revokeSession, this::logUnknownSub);
      case ACCOUNT_DELETE ->
        findUser(event.sub()).ifPresentOrElse(this::softDelete, this::logUnknownSub);
      default ->
        SocialIntegrationLog.info(
            log,
            notificationContext(),
            "Unrecognized Apple notification type: " + event.type());
    }
  }

  private SocialLogContext notificationContext() {
    return SocialLogContext.of(
        SocialProvider.APPLE,
        SocialIntegrationAction.APPLE_NOTIFICATION_PROCESS);
  }

  private Optional<User> findUser(String sub) {
    return userRepository.findByProviderAndSocialId(SocialProvider.APPLE, sub);
  }

  private void revokeSession(User user) {
    refreshTokenService.revokeAllForUser(user.getId());
  }

  private void softDelete(User user) {
    user.markDeleted();
    refreshTokenService.revokeAllForUser(user.getId());
  }

  private void logUnknownSub() {
    SocialIntegrationLog.info(
        log,
        notificationContext(),
        "Apple notification sub does not match any user. no-op");
  }
}
