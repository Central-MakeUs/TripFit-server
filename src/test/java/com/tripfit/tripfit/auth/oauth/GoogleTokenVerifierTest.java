package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleTokenVerifierTest {

  private GoogleTokenVerifier googleTokenVerifier;

  @BeforeEach
  void setUp() {
    OAuthProperties oAuthProperties = new OAuthProperties();
    oAuthProperties.setGoogleClientId("test-google-client-id");
    googleTokenVerifier = new GoogleTokenVerifier(oAuthProperties, new GoogleJwkVerifier());
  }

  @Test
  void verify_malformedToken_throwsSocialTokenInvalid() {
    assertThatThrownBy(() -> googleTokenVerifier.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
  }

  @Test
  void verify_missingClientId_throwsInternalError() {
    GoogleTokenVerifier verifierWithoutClientId =
        new GoogleTokenVerifier(new OAuthProperties(), new GoogleJwkVerifier());

    assertThatThrownBy(() -> verifierWithoutClientId.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
  }
}
