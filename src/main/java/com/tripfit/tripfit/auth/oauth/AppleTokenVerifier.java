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
public class AppleTokenVerifier implements SocialTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(AppleTokenVerifier.class);

  private final OAuthProperties oAuthProperties;

  private final AppleJwkVerifier appleJwkVerifier;

  public AppleTokenVerifier(OAuthProperties oAuthProperties, AppleJwkVerifier appleJwkVerifier) {
    this.oAuthProperties = oAuthProperties;
    this.appleJwkVerifier = appleJwkVerifier;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.APPLE;
  }

  // TODO: iss(https://appleid.apple.com) 명시 검증을 추가해야 한다. JWKS 소스가 애플 전용이라 실질적인 위험은 낮지만,
  // AppleNotificationVerifier처럼 iss까지 명시 검증하는 편이 일관적이다.
  @Override
  public OAuthProfile verify(String token) {

    List<String> allowedAudiences = oAuthProperties.getAppleAudiences();
    if (allowedAudiences.isEmpty()) {

      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Apple client ID is not configured");
    }

    return JwtClaimsVerificationSupport.verify(
        () -> buildProfile(token, allowedAudiences),
        SocialProvider.APPLE,
        log,
        "Apple");
  }

  private OAuthProfile buildProfile(String token, List<String> allowedAudiences)
      throws ParseException, JOSEException, BadJOSEException {
    JWTClaimsSet claims = appleJwkVerifier.verify(token);
    String matchedClientId = findMatchedAudience(claims, allowedAudiences);
    if (matchedClientId == null) {
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
    return new OAuthProfile(
        SocialProvider.APPLE,
        subject,
        claims.getStringClaim("email"),
        null,
        null,
        matchedClientId);
  }

  private String findMatchedAudience(JWTClaimsSet claims, List<String> allowedAudiences)
      throws ParseException {
    List<String> audiences = claims.getAudience();
    if (audiences == null) {
      return null;
    }
    return audiences.stream().filter(allowedAudiences::contains).findFirst().orElse(null);
  }
}
