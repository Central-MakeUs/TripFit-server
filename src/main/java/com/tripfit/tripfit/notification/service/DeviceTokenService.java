package com.tripfit.tripfit.notification.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class DeviceTokenService {

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final UserLookupService userLookupService;

  // 사용자 디바이스의 푸시 알림 토큰(FCM Token)을 등록하거나 갱신합니다.
  @Transactional
  public void registerToken(UUID userId, DeviceTokenRegisterRequest request) {
    requireNonBlankToken(request.token());
    User user = userLookupService.requireUser(userId);
    userDeviceTokenRepository.upsertToken(
        UUID.randomUUID().toString(),
        user.getId().toString(),
        request.token(),
        request.deviceType().name());
  }

  // 등록된 디바이스 토큰을 삭제하여 알림 수신을 해제합니다.
  @Transactional
  public void unregisterToken(UUID userId, String token) {
    requireNonBlankToken(token);
    if (userDeviceTokenRepository.deleteByTokenAndUser_Id(token, userId) == 0) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
    }
  }

  // FCM이 더는 유효하지 않다고 응답한 토큰을 지운다. 발송은 트랜잭션 밖에서 끝난 뒤이므로 삭제만 짧은 트랜잭션으로 연다.
  @Transactional
  public void deleteInvalidTokens(Collection<String> tokens) {
    userDeviceTokenRepository.deleteByTokenIn(tokens);
  }

  private static void requireNonBlankToken(String token) {
    if (token == null || token.isBlank()) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);
    }
  }
}
