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
import java.net.URL;
import java.text.ParseException;

// 원격 JWKS 공개키로 RS256 서명 JWT를 검증하는 공통 로직 — provider별 서브클래스는 JWKS URL만 다름
abstract class AbstractRemoteJwkVerifier {

  private final JWKSource<SecurityContext> keySource;

  protected AbstractRemoteJwkVerifier(URL jwkUrl) {
    // RemoteJWKSet은 내부적으로 키를 캐시함 — 인스턴스를 재사용해야 매 검증마다 재조회하지 않음
    this.keySource = new RemoteJWKSet<>(jwkUrl);
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
