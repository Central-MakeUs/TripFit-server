package com.tripfit.tripfit.auth.oauth;

import java.net.MalformedURLException;
import java.net.URL;
import org.springframework.stereotype.Component;

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
