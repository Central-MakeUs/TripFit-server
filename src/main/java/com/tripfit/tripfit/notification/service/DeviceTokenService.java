package com.tripfit.tripfit.notification.service;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 디바이스 토큰 등록·해제 — 재로그인 시 소유자 재할당(D7)
public class DeviceTokenService {

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final UserLookupService userLookupService;

  public DeviceTokenService(
      UserDeviceTokenRepository userDeviceTokenRepository, UserLookupService userLookupService) {
    this.userDeviceTokenRepository = userDeviceTokenRepository;
    this.userLookupService = userLookupService;
  }

  // 토큰을 등록한다 — 기존 토큰이 다른 유저 소유면 재할당, 없으면 신규 저장(D7)
  @Transactional
  public void registerToken(UUID userId, DeviceTokenRegisterRequest request) {
    if (request.token() == null || request.token().isBlank()) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);
    }
    User user = userLookupService.requireUser(userId);
    userDeviceTokenRepository
        .findByToken(request.token())
        .ifPresentOrElse(
            existing -> existing.reassign(user, request.deviceType()),
            () -> userDeviceTokenRepository.save(
                new UserDeviceToken(user, request.token(), request.deviceType())));
  }

  // 로그아웃 시 본인 토큰만 해제 — 본인 것이 아니면 404
  @Transactional
  public void unregisterToken(UUID userId, String token) {
    if (token == null || token.isBlank()) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);
    }
    if (!userDeviceTokenRepository.existsByTokenAndUser_Id(token, userId)) {
      throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
    }
    userDeviceTokenRepository.deleteByTokenAndUser_Id(token, userId);
  }
}
