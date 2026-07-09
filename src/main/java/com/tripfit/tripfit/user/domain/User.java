package com.tripfit.tripfit.user.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "user",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "social_id"}))
@Schema(description = "TripFit 서비스 사용자. 식별 키는 (provider, social_id)")
public class User extends SoftDeleteEntity {

  @Schema(description = "사용자 고유 ID (TripFit 내부 PK)", example = "1")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Schema(description = "소셜 제공자 고유 사용자 ID (Google/Apple `sub`, Kakao `id`)", example = "1234567890")
  @Column(nullable = false)
  private String socialId;

  @Schema(description = "소셜 로그인 제공자")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SocialProvider provider;

  @Schema(description = "소셜 계정 이메일. Apple relay·미제공 시 null. UNIQUE·식별 키 아님", nullable = true,
      example = "user@example.com")
  @Column
  private String email;

  @Schema(description = "소셜 provider 표시명 (prefill·참고용). 미제공 시 null — fallback 없음", nullable = true,
      example = "홍길동")
  @Column
  private String nickname;

  @Schema(
      description = "프로필 이미지 URL. wave 1(A안): provider CDN URL 그대로. wave 4(B안): TripFit S3 URL 예정",
      nullable = true, example = "https://lh3.googleusercontent.com/a/example")
  @Column
  private String profileImageUrl;

  protected User() {}

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
  }

  public Long getId() {
    return id;
  }

  public String getSocialId() {
    return socialId;
  }

  public void setSocialId(String socialId) {
    this.socialId = socialId;
  }

  public SocialProvider getProvider() {
    return provider;
  }

  public void setProvider(SocialProvider provider) {
    this.provider = provider;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getProfileImageUrl() {
    return profileImageUrl;
  }

  public void setProfileImageUrl(String profileImageUrl) {
    this.profileImageUrl = profileImageUrl;
  }
}
