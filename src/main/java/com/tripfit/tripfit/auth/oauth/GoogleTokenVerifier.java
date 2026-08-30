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

  // 구글 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함
  // TODO: iss(https://accounts.google.com | accounts.google.com) 명시 검증 추가 — JWKS 소스가 구글 전용이라
  // 실질 위험은 낮지만, AppleNotificationVerifier처럼 iss까지 명시 검증하는 편이 일관적
  @Override
  public OAuthProfile verify(String token) {
    // 1. 허용된 구글 클라이언트 ID 목록이 설정돼 있는지 확인함
    List<String> allowedAudiences = oAuthProperties.getGoogleClientIds();
    if (allowedAudiences.isEmpty()) {
      // 클라이언트 잘못이 아니라 서버 배포 설정 누락 — 500으로 구분
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Google client ID is not configured");
    }
    // 2. 서명·클레임 검증(예외 매핑은 JwtClaimsVerificationSupport 공용)과 audience 매칭·프로필 생성을 함께 수행함
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
