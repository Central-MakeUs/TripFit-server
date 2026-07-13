package com.tripfit.tripfit.user.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "social_id"}))
@Schema(description = "TripFit 서비스 사용자. 식별 키는 (provider, social_id)")
public class User extends SoftDeleteEntity {

  @Schema(
      description = "사용자 고유 ID (TripFit 내부 PK, UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  private UUID id;

  @Schema(description = "소셜 제공자 고유 사용자 ID (Google/Apple `sub`, Kakao `id`)", example = "1234567890")
  @Column(nullable = false)
  private String socialId;

  @Schema(description = "소셜 로그인 제공자")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SocialProvider provider;

  @Schema(
      description = "소셜 계정 이메일. Apple relay·미제공 시 null. UNIQUE·식별 키 아님",
      nullable = true,
      example = "user@example.com")
  @Column
  private String email;

  @Schema(description = "유저 입력 이름 (필수, PATCH profile). 미입력 시 null", nullable = true, example = "길동")
  @Column(name = "first_name")
  private String firstName;

  @Schema(description = "유저 입력 성 (필수, PATCH profile). 미입력 시 null", nullable = true, example = "홍")
  @Column(name = "last_name")
  private String lastName;

  @Schema(
      description = "소셜 provider 표시명 (prefill·참고용). 미제공 시 null — fallback 없음",
      nullable = true,
      example = "홍길동")
  @Column
  private String nickname;

  @Schema(
      description = "프로필 이미지 URL. wave 1(A안): provider CDN URL 그대로. wave 4(B안): TripFit S3 URL 예정",
      nullable = true,
      example = "https://lh3.googleusercontent.com/a/example")
  @Column(name = "profile_image_url")
  private String profileImageUrl;

  @Schema(description = "Google Calendar OAuth 연동 여부", example = "false")
  @Column(name = "is_google_calendar_connected", nullable = false)
  private boolean isGoogleCalendarConnected;

  @Schema(description = "유저의 Schedule(근무·연차 정보) 저장 여부", example = "false")
  @Column(name = "is_schedule_registered", nullable = false)
  private boolean isScheduleRegistered;

  @Schema(description = "건너뛰기 가능한 온보딩(캘린더/일정 입력) 전체 완료 여부", example = "false")
  @Column(name = "is_optional_onboarding_completed", nullable = false)
  private boolean isOptionalOnboardingCompleted;

  public User(
      String socialId,
      SocialProvider provider,
      String email,
      String nickname,
      String profileImageUrl) {
    this.socialId = socialId;
    this.provider = provider;
    this.email = email;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
    this.isGoogleCalendarConnected = false;
    this.isScheduleRegistered = false;
    this.isOptionalOnboardingCompleted = false;
  }

  // 성·이름이 모두 입력됐는지 확인함 (온보딩 필수 프로필 완료)
  public boolean hasProfileNameComplete() {
    return firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank();
  }
}
