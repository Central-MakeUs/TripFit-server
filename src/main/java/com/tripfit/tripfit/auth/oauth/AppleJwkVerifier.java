package com.tripfit.tripfit.auth.oauth;

import java.net.MalformedURLException;
import java.net.URL;
import org.springframework.stereotype.Component;

// Apple JWKS(https://appleid.apple.com/auth/keys) 공개키로 RS256 서명 JWT를 검증함
// — id_token(AppleTokenVerifier)·S2S notification(AppleNotificationVerifier)이 공유
@Component
public class AppleJwkVerifier extends AbstractRemoteJwkVerifier {

  private static final URL APPLE_JWK_URL;

  static {
    try {
      APPLE_JWK_URL = new URL("https://appleid.apple.com/auth/keys");
    } catch (MalformedURLException exception) {
      throw new IllegalStateException("Invalid Apple JWK URL", exception);
    }
  }

  public AppleJwkVerifier() {
    super(APPLE_JWK_URL);
  }
}
