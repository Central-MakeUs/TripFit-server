package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.proc.BadJWTException;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.text.ParseException;
import org.slf4j.Logger;

// Apple/Google id_token 검증(JWKS 서명·클레임)의 예외를 AuthErrorCode로 매핑하는 공용 로직 — 두 provider가 동일한
// nimbus 예외 계층을 쓰기 때문에 매핑 골격이 같음. provider별 audience 매칭·프로필 생성은 호출부(supplier) 책임
final class JwtClaimsVerificationSupport {

  private JwtClaimsVerificationSupport() {}

  @FunctionalInterface
  interface ProfileSupplier {

    OAuthProfile get() throws ParseException, JOSEException, BadJOSEException;
  }

  static OAuthProfile verify(
      ProfileSupplier supplier,
      SocialProvider provider,
      Logger log,
      String providerLabel) {
    SocialLogContext context =
        SocialLogContext.of(provider, SocialIntegrationAction.LOGIN_TOKEN_VERIFY);
    try {
      return supplier.get();
    } catch (TripFitException exception) {
      // 비즈니스 검증(audience 매칭·subject 확인)에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (BadJWTException exception) {
      // 만료(exp)·아직 유효하지 않음(nbf) 등 시간 클레임 검증 실패 — 메시지로 만료만 구분
      boolean expired = SocialErrorMessages.containsExpired(exception.getMessage());
      SocialIntegrationLog.warn(
          log,
          context.withProviderError(expired ? "token_expired" : "token_claims_invalid", null),
          providerLabel + " ID token claims verification failed",
          exception);
      throw new TripFitException(
          expired ? AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED
              : AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (BadJOSEException exception) {
      // 서명 불일치 등 그 외 JWT 자체 검증 실패 원인을 로그로 남김
      SocialIntegrationLog.warn(
          log,
          context.withProviderError("signature_invalid", null),
          providerLabel + " ID token signature verification failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (ParseException exception) {
      // 토큰 형식 파싱 실패 원인을 로그로 남김
      SocialIntegrationLog.warn(
          log,
          context.withProviderError("token_malformed", null),
          providerLabel + " ID token parsing failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (JOSEException exception) {
      // RemoteJWKSet 조회 실패 등 provider 접근 자체가 안 되는 경우 — 토큰 문제가 아님
      SocialIntegrationLog.warn(
          log,
          context.withProviderError("jwk_unavailable", null),
          providerLabel + " JWK retrieval failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (RuntimeException exception) {
      // 그 외 예상치 못한 실패 원인을 로그로 남기고 무효 토큰으로 통일
      SocialIntegrationLog.warn(
          log,
          context,
          providerLabel + " token verification failed unexpectedly",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }
}
