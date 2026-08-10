package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.DeviceType;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private UserDeviceTokenRepository userDeviceTokenRepository;

  @Mock
  private UserLookupService userLookupService;

  @InjectMocks
  private DeviceTokenService deviceTokenService;

  @Test
  void registerToken_newToken_savesToken() {
    User user = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    when(userDeviceTokenRepository.findByToken("token-1")).thenReturn(Optional.empty());
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    deviceTokenService.registerToken(
        USER_ID,
        new DeviceTokenRegisterRequest("token-1", DeviceType.ANDROID));

    verify(userDeviceTokenRepository).save(any(UserDeviceToken.class));
  }

  @Test
  void registerToken_existingToken_reassignsOwner() {
    User otherOwner = new User("other-sub", SocialProvider.GOOGLE, "a@example.com", "nick", null);
    User newOwner = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    UserDeviceToken existing = new UserDeviceToken(otherOwner, "token-1", DeviceType.IOS);
    when(userDeviceTokenRepository.findByToken("token-1")).thenReturn(Optional.of(existing));
    when(userLookupService.requireUser(USER_ID)).thenReturn(newOwner);

    deviceTokenService.registerToken(
        USER_ID,
        new DeviceTokenRegisterRequest("token-1", DeviceType.ANDROID));

    assertThat(existing.getUser()).isEqualTo(newOwner);
    assertThat(existing.getDeviceType()).isEqualTo(DeviceType.ANDROID);
    verify(userDeviceTokenRepository, never()).save(any(UserDeviceToken.class));
  }

  @Test
  void registerToken_blankToken_throwsTokenRequired() {
    assertThatThrownBy(
        () -> deviceTokenService.registerToken(
            USER_ID,
            new DeviceTokenRegisterRequest(" ", DeviceType.ANDROID)))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);
  }

  @Test
  void unregisterToken_notOwned_throwsNotFound() {
    when(userDeviceTokenRepository.existsByTokenAndUser_Id("token-1", USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> deviceTokenService.unregisterToken(USER_ID, "token-1"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(NotificationErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
  }

  @Test
  void unregisterToken_owned_deletesToken() {
    when(userDeviceTokenRepository.existsByTokenAndUser_Id("token-1", USER_ID)).thenReturn(true);

    deviceTokenService.unregisterToken(USER_ID, "token-1");

    verify(userDeviceTokenRepository).deleteByTokenAndUser_Id("token-1", USER_ID);
  }
}
