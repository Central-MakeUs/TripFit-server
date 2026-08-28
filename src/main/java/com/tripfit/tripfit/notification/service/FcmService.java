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
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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
    try {
      BatchResponse response = firebaseMessaging.sendEach(messages);
      deleteInvalidTokens(tokens, response);
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
