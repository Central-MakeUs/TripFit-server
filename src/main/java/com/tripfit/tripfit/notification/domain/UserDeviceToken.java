package com.tripfit.tripfit.notification.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_device_token")
@Schema(description = "사용자 기기별 FCM 디바이스 토큰. 재로그인 시 소유자가 재할당될 수 있다(D7)")
public class UserDeviceToken extends BaseTimeEntity {

  @Schema(description = "디바이스 토큰 ID (UUID v4)")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "토큰 소유 사용자 — 동일 토큰 재등록 시 재할당(D7)")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Schema(description = "FCM 등록 토큰 값. 기기·앱 설치당 UNIQUE", example = "dEf3...")
  @Column(nullable = false, unique = true, length = 512)
  private String token;

  @Schema(description = "토큰을 발급한 기기 플랫폼")
  @Enumerated(EnumType.STRING)
  @Column(name = "device_type", nullable = false)
  private DeviceType deviceType;

  public UserDeviceToken(User user, String token, DeviceType deviceType) {
    this.user = user;
    this.token = token;
    this.deviceType = deviceType;
  }

  // 재로그인 등으로 다른 유저가 같은 토큰을 다시 등록하면 소유자·플랫폼을 갱신한다(D7)
  public void reassign(User user, DeviceType deviceType) {
    this.user = user;
    this.deviceType = deviceType;
  }
}
