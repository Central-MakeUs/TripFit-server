package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

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
  public SocialProvider getProvider() {
    return SocialProvider.GOOGLE;
  }

  // 구글 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함
  @Override
  public OAuthProfile verify(String token) {
    // 1. 허용된 구글 클라이언트 ID 목록이 설정돼 있는지 확인함
    List<String> allowedAudiences = oAuthProperties.getGoogleClientIds();
    if (allowedAudiences.isEmpty()) {
      // 클라이언트 잘못이 아니라 서버 배포 설정 누락 — 500으로 구분
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Google client ID is not configured");
    }
    try {
      // 2. 토큰 서명을 검증하고 클레임을 파싱함
      JWTClaimsSet claims = processToken(token);
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
          claims.getStringClaim("picture"));
    } catch (TripFitException exception) {
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (BadJWTException exception) {
      // 만료(exp)·아직 유효하지 않음(nbf) 등 시간 클레임 검증 실패 — 메시지로 만료만 구분
      log.warn("Google ID token claims verification failed", exception);
      throw new TripFitException(
          isExpiredMessage(exception.getMessage())
              ? AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED
              : AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (BadJOSEException exception) {
      // 서명 불일치 등 그 외 JWT 자체 검증 실패 원인을 로그로 남김
      log.warn("Google ID token signature verification failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (ParseException exception) {
      // 토큰 형식 파싱 실패 원인을 로그로 남김
      log.warn("Google ID token parsing failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (JOSEException exception) {
      // RemoteJWKSet 조회 실패 등 provider 접근 자체가 안 되는 경우 — 토큰 문제가 아님
      log.warn("Google JWK retrieval failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (Exception exception) {
      // 그 외 예상치 못한 실패 원인을 로그로 남기고 무효 토큰으로 통일
      log.warn("Google token verification failed unexpectedly", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }

  // nimbus 예외 메시지에 만료 문구가 포함됐는지 확인함 — nimbus가 만료를 별도 예외 타입으로 노출하지 않아 문자열로 판별
  private boolean isExpiredMessage(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains("expired");
  }

  private JWTClaimsSet processToken(String token)
      throws ParseException, JOSEException, BadJOSEException, java.net.MalformedURLException {
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(GOOGLE_JWK_URL);
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
    processor.setJWSKeySelector(keySelector);
    return processor.process(token, null);
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
