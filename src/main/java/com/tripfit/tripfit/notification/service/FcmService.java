package com.tripfit.tripfit.notification.service;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.tripfit.tripfit.notification.domain.LandingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

  private static final Logger log = LoggerFactory.getLogger(FcmService.class);

  private static final int BATCH_SIZE = 500;

  // 배치 하나의 응답을 기다리는 최대 시간이다. Firebase SDK는 503 응답에 최대 4번까지, 간격을 최대 60초로 늘려 가며 다시
  // 보내므로 연결·응답 타임아웃만으로는 끝나지 않을 수 있다. 이 시간이 지나면 기다리기를 멈추고 남은 발송을 취소한다.
  static final long SEND_DEADLINE_SECONDS = 30;

  private final FirebaseMessaging firebaseMessaging;

  private final DeviceTokenService deviceTokenService;

  public FcmService(
      @Lazy FirebaseMessaging firebaseMessaging,
      DeviceTokenService deviceTokenService) {
    this.firebaseMessaging = firebaseMessaging;
    this.deviceTokenService = deviceTokenService;
  }

  // 여러 기기 토큰으로 동일한 푸시 알림을 발송합니다.
  // 알림 payload에는 앱 진입 시 활용될 데이터(landingType, tripId 등)가 포함되며,
  // 500건 단위로 나누어(Batch) 전송합니다.
  public void sendMulticast(
      Map<String, UUID> historyIdByToken,
      String title,
      String body,
      LandingType landingType,
      UUID tripId) {
    if (historyIdByToken.isEmpty()) {
      return;
    }
    List<String> tokens = new ArrayList<>(historyIdByToken.keySet());
    for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));
      sendBatch(batch, historyIdByToken, title, body, landingType, tripId);
    }
  }

  // 최대 500개의 토큰을 묶어 한 번에 FCM 서버로 전송합니다.
  // 전송 결과 중 실패(Unregistered, InvalidArgument)한 토큰은 DB에서 자동 삭제합니다.
  // 트랜잭션 밖에서 호출되므로 FCM을 기다리는 동안 DB 커넥션을 쥐지 않습니다.
  @SuppressWarnings("deprecation")
  private void sendBatch(
      List<String> tokens,
      Map<String, UUID> historyIdByToken,
      String title,
      String body,
      LandingType landingType,
      UUID tripId) {
    Notification notification = Notification.builder().setTitle(title).setBody(body).build();
    List<Message> messages =
        tokens.stream()
            .map(
                token -> {
                  Message.Builder builder =
                      Message.builder()
                          .setToken(token)
                          .setNotification(notification)

                          .putData("id", historyIdByToken.get(token).toString())
                          .putData("landingType", landingType.name());
                  if (tripId != null) {
                    builder.putData("tripId", tripId.toString());
                  }
                  return builder.build();
                })
            .toList();
    ApiFuture<BatchResponse> future = null;
    try {
      future = firebaseMessaging.sendEachAsync(messages);
      BatchResponse response = future.get(SEND_DEADLINE_SECONDS, TimeUnit.SECONDS);
      deleteInvalidTokens(tokens, response);
    } catch (TimeoutException exception) {
      future.cancel(true);
      log.warn("FCM 응답이 {}초 안에 오지 않아 발송을 취소합니다. tokens={}", SEND_DEADLINE_SECONDS, tokens.size());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      log.warn("FCM 발송 대기 중 스레드가 중단돼 발송을 취소합니다. tokens={}", tokens.size());
    } catch (Exception exception) {

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
      deviceTokenService.deleteInvalidTokens(invalidTokens);
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
