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

      throw exception;
    } catch (BadJWTException exception) {

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

      SocialIntegrationLog.warn(
          log,
          context.withProviderError("signature_invalid", null),
          providerLabel + " ID token signature verification failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (ParseException exception) {

      SocialIntegrationLog.warn(
          log,
          context.withProviderError("token_malformed", null),
          providerLabel + " ID token parsing failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (JOSEException exception) {

      SocialIntegrationLog.warn(
          log,
          context.withProviderError("jwk_unavailable", null),
          providerLabel + " JWK retrieval failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (RuntimeException exception) {

      SocialIntegrationLog.warn(
          log,
          context,
          providerLabel + " token verification failed unexpectedly",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }
}
