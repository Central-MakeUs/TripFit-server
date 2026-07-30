package com.tripfit.tripfit.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Apple S2S notification outer JWT의 서명·iss·aud를 검증하고 events 클레임을 파싱함 (AppleJwkVerifier로 서명 검증 재사용)
@Component
public class AppleNotificationVerifier {

  private static final Logger log = LoggerFactory.getLogger(AppleNotificationVerifier.class);

  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  // sub(Apple user id)가 반드시 있어야 처리 가능한 이벤트 — 없으면 payload 자체를 신뢰할 수 없음
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

  // outer JWT(payload) 서명·iss·aud를 검증하고 events 클레임(JSON 문자열)을 파싱해 반환함
  public AppleNotificationEvent verify(String payload) {
    String appleClientId = oAuthProperties.getAppleClientId();
    if (appleClientId == null || appleClientId.isBlank()) {
      throw new TripFitException(
          CommonErrorCode.INTERNAL_ERROR, "Apple client ID is not configured");
    }
    try {
      // 1. outer JWT 서명을 검증함 (만료·서명 불일치는 BadJOSEException 계열로 위임)
      JWTClaimsSet claims = appleJwkVerifier.verify(payload);

      // 2. iss가 Apple이 맞는지 확인함 — 다른 발급자의 유효 서명 JWT를 재사용하는 위조 시도 차단
      if (!APPLE_ISSUER.equals(claims.getIssuer())) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_ISSUER_INVALID);
      }

      // 3. aud가 우리 client_id인지 확인함 — 다른 서비스 앞으로 발급된 알림의 오배달 차단
      List<String> audiences = claims.getAudience();
      if (audiences == null || !audiences.contains(appleClientId)) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_AUDIENCE_INVALID);
      }

      // 4. events 클레임은 JSON 문자열로 인코딩돼 있어 한 번 더 파싱이 필요함
      String eventsJson = claims.getStringClaim("events");
      if (eventsJson == null || eventsJson.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      AppleNotificationEvent event =
          OBJECT_MAPPER.readValue(eventsJson, AppleNotificationEvent.class);

      // 5. type·(필요 시) sub 누락은 이후 처리 단계에서 NPE·오처리로 새는 대신 여기서 바로 400 처리
      if (event.type() == null || event.type().isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      if (EVENTS_REQUIRING_SUB.contains(event.type())
          && (event.sub() == null || event.sub().isBlank())) {
        throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
      }
      return event;
    } catch (TripFitException exception) {
      // 위에서 만든 검증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (BadJOSEException exception) {
      // 서명 불일치·만료(BadJWTException은 BadJOSEException의 하위 타입) 등 서명 자체 검증 실패
      log.warn("Apple notification signature verification failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_SIGNATURE_INVALID);
    } catch (ParseException | JsonProcessingException exception) {
      log.warn("Apple notification payload parsing failed", exception);
      throw new TripFitException(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
    } catch (JOSEException exception) {
      // RemoteJWKSet 조회 실패 등 Apple JWK 접근 자체가 안 되는 경우 — 500으로 구분해 Apple 재시도를 유도함
      log.warn("Apple JWK retrieval failed", exception);
      throw new TripFitException(CommonErrorCode.INTERNAL_ERROR, "Apple JWK 조회에 실패했습니다.");
    } catch (Exception exception) {
      // Apple payload 문제가 아니라 우리 쪽 예상치 못한 버그일 수 있어 400으로 숨기지 않고 500으로 노출·로그 남김
      log.error("Apple notification verification failed unexpectedly", exception);
      throw new TripFitException(CommonErrorCode.INTERNAL_ERROR, "Apple 알림 처리 중 오류가 발생했습니다.");
    }
  }
}
