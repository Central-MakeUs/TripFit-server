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
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import org.springframework.stereotype.Component;

// Apple JWKS(https://appleid.apple.com/auth/keys) 공개키로 RS256 서명 JWT를 검증함
// — id_token(AppleTokenVerifier)·S2S notification(AppleNotificationVerifier)이 공유
@Component
public class AppleJwkVerifier {

  private static final URL APPLE_JWK_URL;

  static {
    try {
      APPLE_JWK_URL = new URL("https://appleid.apple.com/auth/keys");
    } catch (MalformedURLException exception) {
      throw new IllegalStateException("Invalid Apple JWK URL", exception);
    }
  }

  private final JWKSource<SecurityContext> keySource;

  public AppleJwkVerifier() {
    // RemoteJWKSet은 내부적으로 키를 캐시함 — 인스턴스를 재사용해야 매 검증마다 재조회하지 않음
    this.keySource = new RemoteJWKSet<>(APPLE_JWK_URL);
  }

  // 서명을 검증하고 클레임을 반환함 — iss/aud/exp 등 클레임 자체의 의미 검증은 호출부 책임
  public JWTClaimsSet verify(String token) throws ParseException, JOSEException, BadJOSEException {
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
    processor.setJWSKeySelector(keySelector);
    return processor.process(token, null);
  }
}
