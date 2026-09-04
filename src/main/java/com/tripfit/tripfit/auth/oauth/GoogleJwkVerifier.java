package com.tripfit.tripfit.auth.oauth;

import java.net.MalformedURLException;
import java.net.URL;
import org.springframework.stereotype.Component;

// Google JWKS(https://www.googleapis.com/oauth2/v3/certs) 공개키로 RS256 서명 JWT를 검증함
@Component
public class GoogleJwkVerifier extends AbstractRemoteJwkVerifier {

  private static final URL GOOGLE_JWK_URL;

  static {
    try {
      GOOGLE_JWK_URL = new URL("https://www.googleapis.com/oauth2/v3/certs");
    } catch (MalformedURLException exception) {
      throw new IllegalStateException("Invalid Google JWK URL", exception);
    }
  }

  public GoogleJwkVerifier() {
    super(GOOGLE_JWK_URL);
  }
}
