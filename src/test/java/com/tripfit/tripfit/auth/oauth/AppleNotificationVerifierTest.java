package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleNotificationVerifierTest {

  private AppleNotificationVerifier appleNotificationVerifier;

  @BeforeEach
  void setUp() {
    OAuthProperties oAuthProperties = new OAuthProperties();
    oAuthProperties.setAppleClientId("test-apple-client-id");
    appleNotificationVerifier =
        new AppleNotificationVerifier(oAuthProperties, new AppleJwkVerifier());
  }

  @Test
  void verify_malformedJwt_throwsInvalidPayload() {
    assertThatThrownBy(() -> appleNotificationVerifier.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_APPLE_NOTIFICATION_INVALID_PAYLOAD);
  }

  @Test
  void verify_missingClientId_throwsInternalError() {
    AppleNotificationVerifier verifierWithoutClientId =
        new AppleNotificationVerifier(new OAuthProperties(), new AppleJwkVerifier());

    assertThatThrownBy(() -> verifierWithoutClientId.verify("not-a-valid-jwt"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
  }
}
