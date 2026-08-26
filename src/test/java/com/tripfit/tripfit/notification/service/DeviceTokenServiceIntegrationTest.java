package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.notification.domain.DeviceType;
import com.tripfit.tripfit.notification.domain.UserDeviceToken;
import com.tripfit.tripfit.notification.dto.DeviceTokenRegisterRequest;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 운영 500 사고(같은 토큰이 이미 다른 유저 소유일 때 저장 실패 후 재조회까지 같은 제약 위반을 던지던 문제)의 실제 재현·회귀 테스트
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class DeviceTokenServiceIntegrationTest {

  @Autowired
  private DeviceTokenService deviceTokenService;

  @Autowired
  private UserDeviceTokenRepository userDeviceTokenRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void registerToken_sameTokenAlreadyOwnedByAnotherUser_reassignsWithoutError() {
    User firstOwner =
        userRepository.save(new User("sub-1", SocialProvider.GOOGLE, "a@example.com", "a", null));
    User secondOwner =
        userRepository.save(new User("sub-2", SocialProvider.GOOGLE, "b@example.com", "b", null));
    deviceTokenService.registerToken(
        firstOwner.getId(),
        new DeviceTokenRegisterRequest("shared-token", DeviceType.IOS));

    assertThatCode(
        () -> deviceTokenService.registerToken(
            secondOwner.getId(),
            new DeviceTokenRegisterRequest("shared-token", DeviceType.ANDROID)))
        .doesNotThrowAnyException();

    UserDeviceToken token = userDeviceTokenRepository.findByToken("shared-token").orElseThrow();
    assertThat(token.getUser().getId()).isEqualTo(secondOwner.getId());
    assertThat(token.getDeviceType()).isEqualTo(DeviceType.ANDROID);
  }

  @Test
  void registerToken_sameUserReRegistersSameToken_updatesDeviceType() {
    User owner =
        userRepository.save(new User("sub-3", SocialProvider.GOOGLE, "c@example.com", "c", null));
    deviceTokenService.registerToken(
        owner.getId(),
        new DeviceTokenRegisterRequest("own-token", DeviceType.ANDROID));

    deviceTokenService
        .registerToken(owner.getId(), new DeviceTokenRegisterRequest("own-token", DeviceType.IOS));

    UserDeviceToken token = userDeviceTokenRepository.findByToken("own-token").orElseThrow();
    assertThat(token.getUser().getId()).isEqualTo(owner.getId());
    assertThat(token.getDeviceType()).isEqualTo(DeviceType.IOS);
  }
}
