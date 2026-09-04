package com.tripfit.tripfit.auth.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.oauth.AppleNotificationEvent;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Apple S2S notification 이벤트 타입별로 user.deleted_at·refresh_token을 반영함
// (docs/specs/auth-apple-server-notifications.md)
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

  // 이벤트 type별 최소 처리 — 미인식 type·존재하지 않는 sub는 로그만 남기고 no-op(idempotent, 재시도 방지 위해 200 유지)
  @Transactional
  public void handle(AppleNotificationEvent event) {
    switch (event.type()) {
      case EMAIL_ENABLED, EMAIL_DISABLED -> {
        // MVP에 user.email 미보유 — 로그만 (wave 4 email 컬럼 추가 시 반영)
        SocialIntegrationLog.info(
            log,
            notificationContext(),
            "Apple notification " + event.type() + " received (email 컬럼 미보유 — 로그만)");
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

  // consent-revoked: 계정은 유지하고 로그아웃만(refresh_token 전부 폐기)
  private void revokeSession(User user) {
    refreshTokenService.revokeAllForUser(user.getId());
  }

  // account-delete: Apple ID 자체가 영구 삭제됐으므로 soft delete + refresh_token 전부 폐기
  private void softDelete(User user) {
    user.setDeletedAt(LocalDateTime.now());
    refreshTokenService.revokeAllForUser(user.getId());
  }

  private void logUnknownSub() {
    SocialIntegrationLog.info(
        log,
        notificationContext(),
        "Apple notification sub does not match any user — no-op");
  }
}
