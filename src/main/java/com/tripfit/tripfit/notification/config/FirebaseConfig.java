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

  private final FcmProperties fcmProperties;

  public FirebaseConfig(FcmProperties fcmProperties) {
    this.fcmProperties = fcmProperties;
  }

  // FCM 발송 시 최초 1회 지연 초기화됨 — 키 미설정 로컬·테스트는 부팅에 영향 없음(GoogleCalendarTokenCrypto와 동일 패턴)
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
      FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
      FirebaseApp app =
          FirebaseApp.getApps().isEmpty()
              ? FirebaseApp.initializeApp(options)
              : FirebaseApp.getInstance();
      return FirebaseMessaging.getInstance(app);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to initialize Firebase", exception);
    }
  }
}
