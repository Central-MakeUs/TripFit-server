package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
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

  // 애플 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함
  @Override
  public OAuthProfile verify(String token) {
    // 1. 애플 서비스 ID가 설정돼 있는지 확인함
    String appleClientId = oAuthProperties.getAppleClientId();
    if (appleClientId == null || appleClientId.isBlank()) {
      // 클라이언트 잘못이 아니라 서버 배포 설정 누락 — 500으로 구분
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Apple client ID is not configured");
    }
    try {
      // 2. 토큰 서명을 검증하고 클레임을 파싱함
      JWTClaimsSet claims = processToken(token);
      if (!hasValidAudience(claims, appleClientId)) {
        throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
      }
      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
      }
      return new OAuthProfile(
          SocialProvider.APPLE, subject, claims.getStringClaim("email"), null, null);
    } catch (TripFitException exception) {
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (BadJWTException exception) {
      // 만료(exp)·아직 유효하지 않음(nbf) 등 시간 클레임 검증 실패 — 메시지로 만료만 구분
      log.warn("Apple ID token claims verification failed", exception);
      throw new TripFitException(
          isExpiredMessage(exception.getMessage())
              ? AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED
              : AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (BadJOSEException exception) {
      // 서명 불일치 등 그 외 JWT 자체 검증 실패 원인을 로그로 남김
      log.warn("Apple ID token signature verification failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (ParseException exception) {
      // 토큰 형식 파싱 실패 원인을 로그로 남김
      log.warn("Apple ID token parsing failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    } catch (JOSEException exception) {
      // RemoteJWKSet 조회 실패 등 provider 접근 자체가 안 되는 경우 — 토큰 문제가 아님
      log.warn("Apple JWK retrieval failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (Exception exception) {
      // 그 외 예상치 못한 실패 원인을 로그로 남기고 무효 토큰으로 통일
      log.warn("Apple token verification failed unexpectedly", exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }

  // nimbus 예외 메시지에 만료 문구가 포함됐는지 확인함 — nimbus가 만료를 별도 예외 타입으로 노출하지 않아 문자열로 판별
  private boolean isExpiredMessage(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains("expired");
  }

  private JWTClaimsSet processToken(String token)
      throws ParseException, JOSEException, BadJOSEException {
    return appleJwkVerifier.verify(token);
  }

  private boolean hasValidAudience(JWTClaimsSet claims, String appleClientId)
      throws ParseException {
    List<String> audiences = claims.getAudience();
    return audiences != null && audiences.contains(appleClientId);
  }
}
