package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleTokenVerifierTest {

  private AppleTokenVerifier appleTokenVerifier;

  @BeforeEach
  void setUp() {
    OAuthProperties oAuthProperties = new OAuthProperties();
    oAuthProperties.setAppleBundleId("test-apple-bundle-id");
    oAuthProperties.setAppleServiceId("test-apple-service-id");
    appleTokenVerifier = new AppleTokenVerifier(oAuthProperties, new AppleJwkVerifier());
  }

  @Test
  void verify_malformedToken_throwsSocialTokenInvalid() {
    assertThatThrownBy(() -> appleTokenVerifier.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
  }

  @Test
  void verify_missingClientId_throwsInternalError() {
    AppleTokenVerifier verifierWithoutClientId =
        new AppleTokenVerifier(new OAuthProperties(), new AppleJwkVerifier());

    assertThatThrownBy(() -> verifierWithoutClientId.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
  }
}
