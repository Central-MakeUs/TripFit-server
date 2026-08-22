package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FcmServiceTest {

  private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

  private final UserDeviceTokenRepository userDeviceTokenRepository =
      mock(UserDeviceTokenRepository.class);

  private final FcmService fcmService =
      new FcmService(firebaseMessaging, userDeviceTokenRepository);

  @Test
  void sendMulticast_whenFirebaseMessagingThrowsRuntimeException_doesNotPropagate()
      throws FirebaseMessagingException {
    // firebaseMessaging은 @Lazy 빈이라 최초 접근 시 초기화 실패(BeanCreationException 등
    // RuntimeException)가 날 수 있음 — 이 경우도 FirebaseMessagingException과 동일하게 흡수돼야
    // 호출자(dispatch)의 REQUIRES_NEW 트랜잭션이 롤백되지 않는다
    when(firebaseMessaging.sendEach(org.mockito.ArgumentMatchers.anyList()))
        .thenThrow(new IllegalArgumentException("Illegal base64 character 25"));

    assertThatCode(
        () -> fcmService.sendMulticast(
            Map.of("token-1", UUID.randomUUID()),
            "제목",
            "본문",
            LandingType.TRAVEL_ROOM_DETAIL,
            UUID.randomUUID()))
        .doesNotThrowAnyException();

    verifyNoInteractions(userDeviceTokenRepository);
  }
}
