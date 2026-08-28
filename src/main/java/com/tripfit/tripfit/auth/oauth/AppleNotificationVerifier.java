package com.tripfit.tripfit.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppleNotificationVerifier {

  private static final Logger log = LoggerFactory.getLogger(AppleNotificationVerifier.class);

  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  private static final Set<String> EVENTS_REQUIRING_SUB =
      Set.of("consent-revoked", "account-delete");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OAuthProperties oAuthProperties;

  private final AppleJwkVerifier appleJwkVerifier;

  public AppleNotificationVerifier(OAuthProperties oAuthProperties,
      AppleJwkVerifier appleJwkVerifier) {
    this.oAuthProperties = oAuthProperties;
    this.appleJwkVerifier = appleJwkVerifier;
  }

  public AppleNotificationEvent verify(String payload) {
    List<String> allowedAudiences = oAuthProperties.getAppleAudiences();
    if (allowedAudiences.isEmpty()) {
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Apple client ID is not configured");
    }
    try {

      JWTClaimsSet claims = appleJwkVerifier.verify(payload);

      if (!APPLE_ISSUER.equals(claims.getIssuer())) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_ISSUER_INVALID);
      }

      List<String> audiences = claims.getAudience();
      if (audiences == null || audiences.stream().noneMatch(allowedAudiences::contains)) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID);
      }

      String eventsJson = claims.getStringClaim("events");
      if (eventsJson == null || eventsJson.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      AppleNotificationEvent event =
          OBJECT_MAPPER.readValue(eventsJson, AppleNotificationEvent.class);

      if (event.type() == null || event.type().isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      if (EVENTS_REQUIRING_SUB.contains(event.type())
          && (event.sub() == null || event.sub().isBlank())) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      return event;
    } catch (TripFitException exception) {

      throw exception;
    } catch (BadJOSEException exception) {

      SocialIntegrationLog.warn(
          log,
          notificationContext().withProviderError("signature_invalid", null),
          "Apple notification signature verification failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID);
    } catch (ParseException | JsonProcessingException exception) {
      SocialIntegrationLog.warn(
          log,
          notificationContext().withProviderError("payload_malformed", null),
          "Apple notification payload parsing failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
    } catch (JOSEException exception) {

      SocialIntegrationLog.warn(
          log,
          notificationContext().withProviderError("jwk_unavailable", null),
          "Apple JWK retrieval failed",
          exception);
      throw new TripFitException(CommonErrorCode.INTERNAL_ERROR, "Apple JWK 조회에 실패했습니다.");
    } catch (Exception exception) {

      SocialIntegrationLog.error(
          log,
          notificationContext(),
          "Apple notification verification failed unexpectedly",
          exception);
      throw new TripFitException(CommonErrorCode.INTERNAL_ERROR, "Apple 알림 처리 중 오류가 발생했습니다.");
    }
  }

  private SocialLogContext notificationContext() {
    return SocialLogContext.of(
        SocialProvider.APPLE,
        SocialIntegrationAction.APPLE_NOTIFICATION_VERIFY);
  }
}
