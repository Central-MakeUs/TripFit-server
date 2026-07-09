package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.auth.config.OAuthProperties;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

  private static final URL GOOGLE_JWK_URL;

  static {
    try {
      GOOGLE_JWK_URL = new URL("https://www.googleapis.com/oauth2/v3/certs");
    } catch (MalformedURLException exception) {
      throw new IllegalStateException("Invalid Google JWK URL", exception);
    }
  }

  private final OAuthProperties oAuthProperties;

  public GoogleTokenVerifier(OAuthProperties oAuthProperties) {
    this.oAuthProperties = oAuthProperties;
  }

  @Override
  // 이 검증기가 담당하는 소셜 제공자를 구글로 반환함
  public SocialProvider getProvider() {
    return SocialProvider.GOOGLE;
  }

  @Override
  // 구글 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함
  public OAuthProfile verify(String token) {
    // 1. 허용된 구글 클라이언트 ID 목록이 설정돼 있는지 확인함
    List<String> allowedAudiences = oAuthProperties.getGoogleClientIds();
    if (allowedAudiences.isEmpty()) {
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN,
          "Google client ID is not configured");
    }
    try {
      // 2. 토큰 서명을 검증하고 클레임을 파싱함
      JWTClaimsSet claims = processToken(token);
      if (!hasValidAudience(claims, allowedAudiences)) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }
      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }
      return new OAuthProfile(
          SocialProvider.GOOGLE,
          subject,
          claims.getStringClaim("email"),
          claims.getStringClaim("name"),
          claims.getStringClaim("picture"));
    } catch (TripFitException exception) {
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (Exception exception) {
      // 외부 공개키 조회나 JWT 처리 실패 시 유효하지 않은 토큰으로 처리함
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  // 구글 공개키를 사용해 ID 토큰 서명을 검증하고 클레임을 반환함
  private JWTClaimsSet processToken(String token)
      throws ParseException, JOSEException, BadJOSEException, java.net.MalformedURLException {
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(GOOGLE_JWK_URL);
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
    processor.setJWSKeySelector(keySelector);
    return processor.process(token, null);
  }

  // 토큰 audience 중 하나라도 허용된 클라이언트 ID와 일치하는지 확인함
  private boolean hasValidAudience(JWTClaimsSet claims, List<String> allowedAudiences)
      throws ParseException {
    List<String> audiences = claims.getAudience();
    if (audiences == null || audiences.isEmpty()) {
      return false;
    }
    return audiences.stream().anyMatch(allowedAudiences::contains);
  }
}
