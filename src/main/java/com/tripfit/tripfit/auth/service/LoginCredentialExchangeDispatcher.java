package com.tripfit.tripfit.auth.service;

import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

// Apple·Google 로그인 인가 코드 교환을 로그인 요청과 분리해 전용 작업자에게 넘긴다.
// 교환으로 얻는 refresh token은 탈퇴할 때 연결을 끊는 데만 쓰므로, 로그인 응답이 토큰 서버를 기다릴 이유가 없다.
@Component
public class LoginCredentialExchangeDispatcher {

  private static final Logger log =
      LoggerFactory.getLogger(LoginCredentialExchangeDispatcher.class);

  private final Executor socialCredentialExchangeExecutor;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  // 실행기 빈이 둘 이상이라 @Qualifier가 생성자 파라미터에 있어야 하므로 수동 생성자를 쓴다.
  public LoginCredentialExchangeDispatcher(
      @Qualifier("socialCredentialExchangeExecutor") Executor socialCredentialExchangeExecutor,
      AppleCredentialService appleCredentialService,
      GoogleLoginCredentialService googleLoginCredentialService) {
    this.socialCredentialExchangeExecutor = socialCredentialExchangeExecutor;
    this.appleCredentialService = appleCredentialService;
    this.googleLoginCredentialService = googleLoginCredentialService;
  }

  public void submitApple(UUID userId, String authorizationCode, String clientId) {
    submit(
        SocialProvider.APPLE,
        userId,
        authorizationCode,
        () -> appleCredentialService
            .saveIfAuthorizationCodePresent(userId, authorizationCode, clientId));
  }

  public void submitGoogle(UUID userId, String authorizationCode, String redirectUri) {
    submit(
        SocialProvider.GOOGLE,
        userId,
        authorizationCode,
        () -> googleLoginCredentialService
            .saveIfAuthorizationCodePresent(userId, authorizationCode, redirectUri));
  }

  // 작업자와 대기열이 모두 차 있으면 교환을 건너뛴다. 교환이 실패했을 때와 같게 로그인은 성공하고 저장만 생략된다.
  // 대기열이 차는 건 토큰 서버가 느리거나 멈췄을 때라, 로그인 요청에서 직접 교환해도 토큰을 얻지 못한다.
  private void submit(
      SocialProvider provider,
      UUID userId,
      String authorizationCode,
      Runnable exchange) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      return;
    }
    try {
      socialCredentialExchangeExecutor.execute(() -> runQuietly(provider, userId, exchange));
    } catch (TaskRejectedException exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(provider, SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(userId),
          "Credential exchange executor is saturated. skipping credential save",
          exception);
    }
  }

  private void runQuietly(SocialProvider provider, UUID userId, Runnable exchange) {
    try {
      exchange.run();
    } catch (RuntimeException exception) {
      SocialIntegrationLog.error(
          log,
          SocialLogContext.of(provider, SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withUserId(userId),
          "Credential exchange failed unexpectedly",
          exception);
    }
  }
}
