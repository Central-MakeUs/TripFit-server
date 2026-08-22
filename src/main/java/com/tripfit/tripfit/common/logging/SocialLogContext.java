package com.tripfit.tripfit.common.logging;

import com.tripfit.tripfit.user.domain.SocialProvider;
import java.util.UUID;

// 소셜 로그인·Google Calendar 연동 구조화 로그의 공통 필드 — SocialIntegrationLog가 MDC로 채운다
public record SocialLogContext(
    SocialProvider provider,
    SocialIntegrationAction action,
    UUID userId,
    Integer httpStatus,
    String providerErrorReason,
    String providerErrorMessage,
    String trigger,
    String grantedScope
) {

  public static SocialLogContext of(SocialProvider provider, SocialIntegrationAction action) {
    return new SocialLogContext(provider, action, null, null, null, null, null, null);
  }

  public SocialLogContext withUserId(UUID userId) {
    return new SocialLogContext(
        provider, action, userId, httpStatus, providerErrorReason, providerErrorMessage, trigger,
        grantedScope);
  }

  public SocialLogContext withHttpStatus(int httpStatus) {
    return new SocialLogContext(
        provider, action, userId, httpStatus, providerErrorReason, providerErrorMessage, trigger,
        grantedScope);
  }

  // provider 에러 바디는 여기서만 채울 수 있게 해 PiiMasker 마스킹을 강제한다
  public SocialLogContext withProviderError(String reason, String rawMessage) {
    String masked = rawMessage == null ? null : PiiMasker.mask(rawMessage);
    return new SocialLogContext(
        provider, action, userId, httpStatus, reason, masked, trigger, grantedScope);
  }

  public SocialLogContext withTrigger(String trigger) {
    return new SocialLogContext(
        provider, action, userId, httpStatus, providerErrorReason, providerErrorMessage, trigger,
        grantedScope);
  }

  public SocialLogContext withGrantedScope(String grantedScope) {
    return new SocialLogContext(
        provider, action, userId, httpStatus, providerErrorReason, providerErrorMessage, trigger,
        grantedScope);
  }
}
