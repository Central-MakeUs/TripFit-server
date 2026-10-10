package com.tripfit.tripfit.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class FirebaseConfig {

  // Firebase SDK는 타임아웃을 지정하지 않으면 연결과 응답을 끝없이 기다린다.
  // 다른 외부 호출(RestClient 연결 3초·응답 5초)과 같은 값을 걸어, FCM이 멈춰도 발송 스레드가 풀려나게 한다.
  static final int CONNECT_TIMEOUT_MILLIS = 3_000;

  static final int READ_TIMEOUT_MILLIS = 5_000;

  static final int WRITE_TIMEOUT_MILLIS = 5_000;

  private final FcmProperties fcmProperties;

  public FirebaseConfig(FcmProperties fcmProperties) {
    this.fcmProperties = fcmProperties;
  }

  @Bean
  @Lazy
  public FirebaseMessaging firebaseMessaging() {
    String credentialsBase64 = fcmProperties.getCredentialsBase64();
    if (credentialsBase64 == null || credentialsBase64.isBlank()) {
      throw new IllegalStateException("FIREBASE_CREDENTIALS_BASE64 is required for FCM push");
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
      GoogleCredentials credentials =
          GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
      FirebaseOptions options = firebaseOptions(credentials);
      FirebaseApp app =
          FirebaseApp.getApps().isEmpty()
              ? FirebaseApp.initializeApp(options)
              : FirebaseApp.getInstance();
      return FirebaseMessaging.getInstance(app);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to initialize Firebase", exception);
    }
  }

  static FirebaseOptions firebaseOptions(GoogleCredentials credentials) {
    return FirebaseOptions.builder()
        .setCredentials(credentials)
        .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
        .setReadTimeout(READ_TIMEOUT_MILLIS)
        .setWriteTimeout(WRITE_TIMEOUT_MILLIS)
        .build();
  }
}
