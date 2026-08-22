package com.tripfit.tripfit.common.logging;

// 소셜 로그인·Google Calendar 연동 구조화 로그의 action 필드 — 닫힌
// 집합(docs/specs/social-integration-structured-logging.md)
public enum SocialIntegrationAction {
  LOGIN_TOKEN_VERIFY,
  LOGIN_USERINFO_FETCH,
  LOGIN_TOKEN_REVOKE,
  LOGIN_CREDENTIAL_EXCHANGE,
  LOGIN_CREDENTIAL_REVOKE,
  APPLE_NOTIFICATION_VERIFY,
  APPLE_NOTIFICATION_PROCESS,
  CALENDAR_CONNECT,
  CALENDAR_SYNC,
  CALENDAR_TOKEN_REVOKE
}
