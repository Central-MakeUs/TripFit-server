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
// 디바이스 토큰 등록·해제 — 재로그인 시 소유자 재할당(D7)
public class DeviceTokenService {

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final UserLookupService userLookupService;

  // 토큰을 등록한다 — 기존 토큰이 다른 유저 소유면 재할당, 없으면 신규 저장(D7). UNIQUE 경합은 upsert가 원자적으로 흡수
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

  // 로그아웃 시 본인 토큰만 해제 — 본인 것이 아니면 404
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
