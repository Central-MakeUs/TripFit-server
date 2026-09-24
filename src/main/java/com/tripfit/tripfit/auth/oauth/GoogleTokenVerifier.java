package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.text.ParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

  private final OAuthProperties oAuthProperties;

  private final GoogleJwkVerifier googleJwkVerifier;

  public GoogleTokenVerifier(OAuthProperties oAuthProperties, GoogleJwkVerifier googleJwkVerifier) {
    this.oAuthProperties = oAuthProperties;
    this.googleJwkVerifier = googleJwkVerifier;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.GOOGLE;
  }

  @Override
  public OAuthProfile verify(String token) {

    List<String> allowedAudiences = oAuthProperties.getGoogleClientIds();
    if (allowedAudiences.isEmpty()) {

      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Google client ID is not configured");
    }

    return JwtClaimsVerificationSupport.verify(
        () -> buildProfile(token, allowedAudiences),
        SocialProvider.GOOGLE,
        log,
        "Google");
  }

  private OAuthProfile buildProfile(String token, List<String> allowedAudiences)
      throws ParseException, JOSEException, BadJOSEException {
    JWTClaimsSet claims = googleJwkVerifier.verify(token);
    if (!hasValidAudience(claims, allowedAudiences)) {
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
    return new OAuthProfile(
        SocialProvider.GOOGLE,
        subject,
        claims.getStringClaim("email"),
        claims.getStringClaim("name"),
        claims.getStringClaim("picture"),
        null);
  }

  private boolean hasValidAudience(JWTClaimsSet claims, List<String> allowedAudiences)
      throws ParseException {
    List<String> audiences = claims.getAudience();
    if (audiences == null || audiences.isEmpty()) {
      return false;
    }
    return audiences.stream().anyMatch(allowedAudiences::contains);
  }
}
