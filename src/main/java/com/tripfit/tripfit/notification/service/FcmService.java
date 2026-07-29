package com.tripfit.tripfit.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

// FCM 멀티캐스트 발송 — 500건 배치 분할(D6), 무효 토큰(UNREGISTERED/INVALID_ARGUMENT) 자동 삭제
@Service
public class FcmService {

  private static final Logger log = LoggerFactory.getLogger(FcmService.class);

  private static final int BATCH_SIZE = 500;

  private final FirebaseMessaging firebaseMessaging;

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  public FcmService(
      @Lazy FirebaseMessaging firebaseMessaging,
      UserDeviceTokenRepository userDeviceTokenRepository) {
    this.firebaseMessaging = firebaseMessaging;
    this.userDeviceTokenRepository = userDeviceTokenRepository;
  }

  // 토큰 목록에 동일 알림을 발송한다 — 대상 토큰이 0개면 skip(에러 아님)
  public void sendMulticast(
      List<String> tokens,
      String title,
      String body,
      LandingType landingType) {
    if (tokens.isEmpty()) {
      return;
    }
    for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));
      sendBatch(batch, title, body, landingType);
    }
  }

  // token(등록 토큰) 기반 발송 — SDK가 신규 fid(Firebase Installation ID) 필드를 권장하지만 클라이언트는
  // 표준 FCM 등록 토큰만 발급하므로(스펙 데이터 모델) token 유지
  @SuppressWarnings("deprecation")
  private void sendBatch(List<String> tokens, String title, String body, LandingType landingType) {
    Notification notification = Notification.builder().setTitle(title).setBody(body).build();
    List<Message> messages =
        tokens.stream()
            .map(
                token -> Message.builder()
                    .setToken(token)
                    .setNotification(notification)
                    .putData("landingType", landingType.name())
                    .build())
            .toList();
    try {
      BatchResponse response = firebaseMessaging.sendEach(messages);
      deleteInvalidTokens(tokens, response);
    } catch (FirebaseMessagingException exception) {
      // 배치 전체 실패는 다음 이벤트 재시도로 흡수 — 예외를 위로 던지지 않아 이력 저장 흐름을 막지 않음
      log.warn("FCM 멀티캐스트 발송 실패", exception);
    }
  }

  private void deleteInvalidTokens(List<String> tokens, BatchResponse response) {
    List<SendResponse> responses = response.getResponses();
    List<String> invalidTokens = new ArrayList<>();
    for (int i = 0; i < responses.size(); i++) {
      SendResponse sendResponse = responses.get(i);
      if (!sendResponse.isSuccessful() && isInvalidToken(sendResponse.getException())) {
        invalidTokens.add(tokens.get(i));
      }
    }
    if (!invalidTokens.isEmpty()) {
      userDeviceTokenRepository.deleteByTokenIn(invalidTokens);
    }
  }

  private boolean isInvalidToken(FirebaseMessagingException exception) {
    if (exception == null) {
      return false;
    }
    MessagingErrorCode errorCode = exception.getMessagingErrorCode();
    return errorCode == MessagingErrorCode.UNREGISTERED
        || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
  }
}
