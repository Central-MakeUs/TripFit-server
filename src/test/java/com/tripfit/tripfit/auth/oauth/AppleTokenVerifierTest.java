package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleTokenVerifierTest {

  private AppleTokenVerifier appleTokenVerifier;

  @BeforeEach
  void setUp() {
    OAuthProperties oAuthProperties = new OAuthProperties();
    oAuthProperties.setAppleClientId("test-apple-client-id");
    appleTokenVerifier = new AppleTokenVerifier(oAuthProperties);
  }

  @Test
  void verify_invalidToken_throwsAuthInvalidToken() {
    assertThatThrownBy(() -> appleTokenVerifier.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN);
  }

  @Test
  void verify_missingClientId_throwsAuthInvalidToken() {
    AppleTokenVerifier verifierWithoutClientId = new AppleTokenVerifier(new OAuthProperties());

    assertThatThrownBy(() -> verifierWithoutClientId.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_INVALID_TOKEN);
  }
}
