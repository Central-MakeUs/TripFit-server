package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FCM 디바이스 토큰을 발급한 기기 플랫폼")
public enum DeviceType {
  @Schema(description = "Android 기기")
  ANDROID,

  @Schema(description = "iOS 기기")
  IOS,

  @Schema(description = "모바일 웹")
  WEB
}
