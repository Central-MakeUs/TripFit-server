package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
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
    try {
      // 2. 토큰 서명을 검증하고 클레임을 파싱함
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
    } catch (TripFitException exception) {
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (BadJWTException exception) {
      // 만료(exp)·아직 유효하지 않음(nbf) 등 시간 클레임 검증 실패 — 메시지로 만료만 구분
      boolean expired = SocialErrorMessages.containsExpired(exception.getMessage());
      SocialIntegrationLog.warn(
          log,
          verifyContext()
              .withProviderError(expired ? "token_expired" : "token_claims_invalid", null),
          "Google ID token claims verification failed",
          exception);
      throw new TripFitException(
          expired ? AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED
              : AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (BadJOSEException exception) {
      // 서명 불일치 등 그 외 JWT 자체 검증 실패 원인을 로그로 남김
      SocialIntegrationLog.warn(
          log,
          verifyContext().withProviderError("signature_invalid", null),
          "Google ID token signature verification failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (ParseException exception) {
      // 토큰 형식 파싱 실패 원인을 로그로 남김
      SocialIntegrationLog.warn(
          log,
          verifyContext().withProviderError("token_malformed", null),
          "Google ID token parsing failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (JOSEException exception) {
      // RemoteJWKSet 조회 실패 등 provider 접근 자체가 안 되는 경우 — 토큰 문제가 아님
      SocialIntegrationLog.warn(
          log,
          verifyContext().withProviderError("jwk_unavailable", null),
          "Google JWK retrieval failed",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (Exception exception) {
      // 그 외 예상치 못한 실패 원인을 로그로 남기고 무효 토큰으로 통일
      SocialIntegrationLog.warn(
          log,
          verifyContext(),
          "Google token verification failed unexpectedly",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }

  private SocialLogContext verifyContext() {
    return SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.LOGIN_TOKEN_VERIFY);
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
