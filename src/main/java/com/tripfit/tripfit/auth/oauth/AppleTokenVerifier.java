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

  // 애플 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함 — Bundle ID(iOS 네이티브)·Services ID(모바일
  // 브라우저) 중 하나만 맞아도 통과시킴(docs/specs/apple-oauth-multi-audience.md)
  // TODO: iss(https://appleid.apple.com) 명시 검증 추가 — JWKS 소스가 애플 전용이라 실질 위험은 낮지만,
  // AppleNotificationVerifier처럼 iss까지 명시 검증하는 편이 일관적
  @Override
  public OAuthProfile verify(String token) {
    // 1. 허용된 애플 client_id 목록(Bundle ID·Services ID)이 설정돼 있는지 확인함
    List<String> allowedAudiences = oAuthProperties.getAppleAudiences();
    if (allowedAudiences.isEmpty()) {
      // 클라이언트 잘못이 아니라 서버 배포 설정 누락 — 500으로 구분
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Apple client ID is not configured");
    }
    // 2. 서명·클레임 검증(예외 매핑은 JwtClaimsVerificationSupport 공용)과 audience 매칭·프로필 생성을 함께 수행함
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

  // 토큰의 aud 클레임과 허용 목록을 대조해 실제로 매칭된 client_id를 반환함(없으면 null) — 이후 credential 저장·revoke에
  // 재사용하기 위해 매칭값 자체가 필요함
  private String findMatchedAudience(JWTClaimsSet claims, List<String> allowedAudiences)
      throws ParseException {
    List<String> audiences = claims.getAudience();
    if (audiences == null) {
      return null;
    }
    return audiences.stream().filter(allowedAudiences::contains).findFirst().orElse(null);
  }
}
