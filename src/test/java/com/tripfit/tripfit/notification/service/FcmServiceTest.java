package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.tripfit.tripfit.notification.domain.LandingType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class FcmServiceTest {

  private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

  private final DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);

  private final FcmService fcmService = new FcmService(firebaseMessaging, deviceTokenService);

  @Test
  void sendMulticast_whenFirebaseMessagingThrowsRuntimeException_doesNotPropagate() {

    when(firebaseMessaging.sendEachAsync(org.mockito.ArgumentMatchers.anyList()))
        .thenThrow(new IllegalArgumentException("Illegal base64 character 25"));

    assertThatCode(
        () -> fcmService.sendMulticast(
            Map.of("token-1", UUID.randomUUID()),
            "제목",
            "본문",
            LandingType.TRAVEL_ROOM_DETAIL,
            UUID.randomUUID()))
        .doesNotThrowAnyException();

    verifyNoInteractions(deviceTokenService);
  }

  // FCM이 상한 안에 응답하지 않으면 기다리기를 멈추고 남은 발송을 취소한다. 예외는 밖으로 나가지 않는다.
  @Test
  @SuppressWarnings("unchecked")
  void sendMulticast_whenResponseExceedsDeadline_cancelsAndReturns() throws Exception {
    ApiFuture<BatchResponse> future = mock(ApiFuture.class);
    when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
    when(firebaseMessaging.sendEachAsync(anyList())).thenReturn(future);

    assertThatCode(
        () -> fcmService.sendMulticast(
            Map.of("token-1", UUID.randomUUID()),
            "제목",
            "본문",
            LandingType.TRAVEL_ROOM_DETAIL,
            UUID.randomUUID()))
        .doesNotThrowAnyException();

    verify(future).get(FcmService.SEND_DEADLINE_SECONDS, TimeUnit.SECONDS);
    verify(future).cancel(true);
    verifyNoInteractions(deviceTokenService);
  }
}
