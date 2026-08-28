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

abstract class AbstractRemoteJwkVerifier {

  private final JWKSource<SecurityContext> keySource;

  protected AbstractRemoteJwkVerifier(URL jwkUrl) {

    this.keySource = new RemoteJWKSet<>(jwkUrl);
  }

  public JWTClaimsSet verify(String token) throws ParseException, JOSEException, BadJOSEException {
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
    processor.setJWSKeySelector(keySelector);
    return processor.process(token, null);
  }
}
