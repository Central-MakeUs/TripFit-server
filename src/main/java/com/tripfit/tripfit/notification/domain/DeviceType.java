package com.tripfit.tripfit.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        푸시 알림(FCM) 디바이스 토큰이 발급된 기기의 플랫폼 종류입니다.
        """)
public enum DeviceType {
  @Schema(
      description = """
          안드로이드(Android) 기기입니다.
          """)
  ANDROID,
  @Schema(
      description = """
          아이폰(iOS) 기기입니다.
          """)
  IOS,
  @Schema(
      description = """
          모바일 웹(Web) 환경입니다.
          """)
  WEB
}
