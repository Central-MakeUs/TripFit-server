package com.tripfit.tripfit.notification.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class DeviceTokenService {

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final UserLookupService userLookupService;

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

  @Transactional
  public void unregisterToken(UUID userId, String token) {
    requireNonBlankToken(token);
    if (userDeviceTokenRepository.deleteByTokenAndUser_Id(token, userId) == 0) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
    }
  }

  private static void requireNonBlankToken(String token) {
    if (token == null || token.isBlank()) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);
    }
  }
}
