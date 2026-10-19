package com.tripfit.tripfit.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.Test;

class FirebaseConfigTest {

  // Firebase SDK의 기본 타임아웃은 0(무제한)이라, 옵션에 값이 실제로 들어가는지 확인한다.
  @Test
  void firebaseOptions_setsFiniteTimeouts() {
    FirebaseOptions options =
        FirebaseConfig.firebaseOptions(GoogleCredentials.create(new AccessToken("token", null)));

    assertThat(options.getConnectTimeout()).isEqualTo(3_000);
    assertThat(options.getReadTimeout()).isEqualTo(5_000);
    assertThat(options.getWriteTimeout()).isEqualTo(5_000);
  }
}
